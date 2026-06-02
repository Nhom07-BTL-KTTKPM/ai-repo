package iuh.fit.aiservice.recommendation.service;

import iuh.fit.aiservice.client.UserServiceClient;
import iuh.fit.aiservice.dto.client.CustomerProfileResponse;
import iuh.fit.aiservice.model.enums.RecommendationType;
import iuh.fit.aiservice.recommendation.client.CatalogProductClient;
import iuh.fit.aiservice.recommendation.client.dto.CatalogPageResponse;
import iuh.fit.aiservice.recommendation.client.dto.CatalogProductResponse;
import iuh.fit.aiservice.recommendation.config.RecommendationProperties;
import iuh.fit.aiservice.recommendation.engine.RecommendationEngine;
import iuh.fit.aiservice.recommendation.engine.RecommendationEngine.RecommendationContext;
import iuh.fit.aiservice.recommendation.engine.RecommendationEngine.ScoredCandidate;
import iuh.fit.aiservice.recommendation.model.BehaviorEventType;
import iuh.fit.aiservice.recommendation.model.ProductViewLogEntity;
import iuh.fit.aiservice.recommendation.model.RecommendationEntity;
import iuh.fit.aiservice.recommendation.repository.ProductViewLogRepository;
import iuh.fit.aiservice.recommendation.repository.RecommendationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RecommendationGenerator {

    private static final int CATALOG_PAGE_SIZE = 30;
    private final Set<UUID> inFlightCustomers = ConcurrentHashMap.newKeySet();

    private final ProductViewLogRepository viewLogRepository;
    private final RecommendationRepository recommendationRepository;
    private final CatalogProductClient catalogProductClient;
    private final UserServiceClient userServiceClient;
    private final RecommendationEngine recommendationEngine;
    private final RecommendationExplanationService explanationService;
    private final PreferenceInsightService preferenceInsightService;
    private final RecommendationProperties properties;

    public RecommendationGenerator(
            ProductViewLogRepository viewLogRepository,
            RecommendationRepository recommendationRepository,
            CatalogProductClient catalogProductClient,
            UserServiceClient userServiceClient,
            RecommendationEngine recommendationEngine,
            RecommendationExplanationService explanationService,
                PreferenceInsightService preferenceInsightService,
            RecommendationProperties properties
    ) {
        this.viewLogRepository = viewLogRepository;
        this.recommendationRepository = recommendationRepository;
        this.catalogProductClient = catalogProductClient;
        this.userServiceClient = userServiceClient;
        this.recommendationEngine = recommendationEngine;
        this.explanationService = explanationService;
        this.preferenceInsightService = preferenceInsightService;
        this.properties = properties;
    }

    @Async("recommendationExecutor")
    @Transactional
    public CompletableFuture<Void> generateAsync(UUID customerId) {
        if (customerId == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!inFlightCustomers.add(customerId)) {
            log.debug("Skip duplicate recommendation job for {}", customerId);
            return CompletableFuture.completedFuture(null);
        }
        try {
            generateForCustomer(customerId);
        } catch (Exception ex) {
            log.warn("Recommendation generation failed for {}", customerId, ex);
        } finally {
            inFlightCustomers.remove(customerId);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void generateForCustomer(UUID customerId) {
        if (customerId == null) {
            return;
        }

        int viewLogLimit = Math.max(1, properties.getViewLogLimit());
        List<ProductViewLogEntity> logs = viewLogRepository.findByCustomerIdOrderByViewedAtDesc(
                customerId,
                PageRequest.of(0, viewLogLimit)
        );
        if (logs == null || logs.isEmpty()) {
            return;
        }

        Set<UUID> viewedProductIds = logs.stream()
                .map(ProductViewLogEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, CatalogProductResponse> viewedProducts = new HashMap<>();
        Map<UUID, Double> categoryWeights = new HashMap<>();
        Map<UUID, Double> brandWeights = new HashMap<>();

        Instant now = Instant.now();
        double halfLifeDays = properties.getRecencyHalfLifeDays() <= 0
                ? 7.0
                : properties.getRecencyHalfLifeDays();

        for (ProductViewLogEntity log : logs) {
            UUID productId = log.getProductId();
            if (productId == null) {
                continue;
            }

            CatalogProductResponse product = viewedProducts.computeIfAbsent(productId, this::safeGetProduct);
            if (product == null || Boolean.FALSE.equals(product.getIsActive())) {
                continue;
            }

            BehaviorEventType eventType = log.getEventType() == null
                    ? BehaviorEventType.VIEW
                    : log.getEventType();
            double weight = weightFor(eventType) * recencyFactor(log.getViewedAt(), now, halfLifeDays);

            if (product.getCategoryId() != null) {
                categoryWeights.merge(product.getCategoryId(), weight, Double::sum);
            }
            if (product.getBrandId() != null) {
                brandWeights.merge(product.getBrandId(), weight, Double::sum);
            }
        }

        if (categoryWeights.isEmpty() && brandWeights.isEmpty()) {
            return;
        }

        Set<UUID> viewedCategories = categoryWeights.keySet();
        Set<UUID> viewedBrands = brandWeights.keySet();

        List<CatalogProductResponse> candidates = new ArrayList<>();
        for (UUID categoryId : viewedCategories) {
            candidates.addAll(safeGetByCategory(categoryId));
        }
        for (UUID brandId : viewedBrands) {
            candidates.addAll(safeGetByBrand(brandId));
        }

        Map<UUID, CatalogProductResponse> uniqueCandidates = new HashMap<>();
        for (CatalogProductResponse candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            if (viewedProductIds.contains(candidate.getId())) {
                continue;
            }
            if (Boolean.FALSE.equals(candidate.getIsActive())) {
                continue;
            }
            uniqueCandidates.putIfAbsent(candidate.getId(), candidate);
        }

        List<CatalogProductResponse> candidateList = new ArrayList<>(uniqueCandidates.values());
        if (candidateList.isEmpty()) {
            return;
        }

        int maxCandidates = Math.max(properties.getMaxCandidates(), 1);
        if (candidateList.size() > maxCandidates) {
            candidateList = candidateList.subList(0, maxCandidates);
        }

        CustomerProfileResponse profile = safeLoadProfile(customerId);
        double maxCategoryWeight = categoryWeights.values().stream()
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
        double maxBrandWeight = brandWeights.values().stream()
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);

        RecommendationContext context = new RecommendationContext(
            categoryWeights,
            brandWeights,
            maxCategoryWeight,
            maxBrandWeight,
            profile == null ? null : profile.getSkinType(),
            profile == null || profile.getSkinConcerns() == null
                ? Set.of()
                : new HashSet<>(profile.getSkinConcerns()),
            viewedProductIds
        );

        double minScore = Math.max(properties.getMinScore(), 0.2);
        int topK = Math.max(properties.getTopK(), 1);

        List<ScoredCandidate> scored = recommendationEngine.scoreCandidates(candidateList, context).stream()
            .filter(candidate -> candidate.score() >= minScore)
            .collect(Collectors.toList());

        List<CatalogProductResponse> viewedList = viewedProducts.values().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        PreferenceInsightService.PreferenceInsight insight = preferenceInsightService.inferPreferences(
            profile,
            viewedList
        );

        List<ScoredCandidate> boosted = applyAiBoost(scored, insight);

        scored = boosted.stream()
            .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
            .limit(topK)
            .toList();

        if (scored.isEmpty()) {
            return;
        }

        Map<UUID, CatalogProductResponse> productById = new HashMap<>();
        Map<UUID, Double> bestScoreById = new HashMap<>();
        Map<UUID, Integer> occurrenceCount = new HashMap<>();

        for (ScoredCandidate candidate : scored) {
            if (candidate == null || candidate.product() == null || candidate.product().getId() == null) {
                continue;
            }
            UUID productId = candidate.product().getId();
            productById.putIfAbsent(productId, candidate.product());
            bestScoreById.merge(productId, candidate.score(), Math::max);
            occurrenceCount.merge(productId, 1, Integer::sum);
        }

        if (productById.isEmpty()) {
            return;
        }

        if (properties.isReplaceExisting()) {
            recommendationRepository.deleteByCustomerId(customerId);
        }

        Map<UUID, RecommendationEntity> existingByProduct = new HashMap<>();
        if (!properties.isReplaceExisting()) {
            List<RecommendationEntity> existing = recommendationRepository.findByCustomerIdAndProductIdIn(
                    customerId,
                    new ArrayList<>(productById.keySet())
            );
            for (RecommendationEntity entity : existing) {
                existingByProduct.put(entity.getProductId(), entity);
            }
        }

        List<RecommendationEntity> entities = new ArrayList<>();
        for (Map.Entry<UUID, CatalogProductResponse> entry : productById.entrySet()) {
            UUID productId = entry.getKey();
            CatalogProductResponse product = entry.getValue();
            double baseScore = bestScoreById.getOrDefault(productId, 0.0);
            int duplicates = Math.max(occurrenceCount.getOrDefault(productId, 1) - 1, 0);
            RecommendationEntity existing = existingByProduct.get(productId);
            if (existing != null) {
                duplicates += 1;
                if (existing.getScore() != null) {
                    baseScore = Math.max(baseScore, existing.getScore());
                }
            }
            double finalScore = baseScore + (0.1 * duplicates);

            String reason = explanationService.generateReason(profile, product);
            if (reason != null && reason.length() > 500) {
                reason = reason.substring(0, 497) + "...";
            }
            RecommendationEntity entity;
            if (existing != null) {
                entity = existing;
                entity.setScore(finalScore);
                if (entity.getReason() == null || entity.getReason().isBlank()) {
                    entity.setReason(reason);
                }
                if (entity.getType() == null) {
                    entity.setType(RecommendationType.SIMILAR);
                }
            } else {
                entity = RecommendationEntity.builder()
                        .customerId(customerId)
                        .productId(productId)
                        .score(finalScore)
                        .reason(reason)
                        .type(RecommendationType.SIMILAR)
                        .isClicked(false)
                        .createdAt(now)
                        .build();
            }

            entities.add(entity);
        }

        recommendationRepository.saveAll(entities);
    }

    private CustomerProfileResponse safeLoadProfile(UUID customerId) {
        try {
            var response = userServiceClient.getCustomerById(customerId.toString());
            return response == null ? null : response.data();
        } catch (Exception ex) {
            log.warn("Failed to load customer profile {}", customerId, ex);
            return null;
        }
    }

    private CatalogProductResponse safeGetProduct(UUID productId) {
        try {
            return catalogProductClient.getProductById(productId);
        } catch (Exception ex) {
            log.warn("Failed to load product {}", productId, ex);
            return null;
        }
    }

    private double weightFor(BehaviorEventType eventType) {
        if (eventType == null) {
            return properties.getWeightView();
        }
        return switch (eventType) {
            case RECOMMENDATION_CLICK -> properties.getWeightClick();
            case ADD_TO_CART -> properties.getWeightAddToCart();
            case PURCHASE -> properties.getWeightPurchase();
            case VIEW -> properties.getWeightView();
        };
    }

    private double recencyFactor(Instant viewedAt, Instant now, double halfLifeDays) {
        if (viewedAt == null || now == null) {
            return 1.0;
        }
        double days = Math.max(0.0, (double) java.time.Duration.between(viewedAt, now).toHours() / 24.0);
        if (halfLifeDays <= 0.0) {
            return 1.0;
        }
        return Math.pow(0.5, days / halfLifeDays);
    }

    private List<ScoredCandidate> applyAiBoost(
            List<ScoredCandidate> scored,
            PreferenceInsightService.PreferenceInsight insight
    ) {
        if (scored == null || scored.isEmpty() || insight == null) {
            return scored == null ? List.of() : scored;
        }

        Set<String> preferredCategories = normalizeSet(insight.preferredCategories());
        Set<String> preferredBrands = normalizeSet(insight.preferredBrands());
        Set<String> preferredConcerns = normalizeSet(insight.preferredConcerns());

        double categoryBoost = properties.getAiBoostCategory();
        double brandBoost = properties.getAiBoostBrand();
        double concernBoost = properties.getAiBoostConcern();

        return scored.stream()
                .map(candidate -> {
                    CatalogProductResponse product = candidate.product();
                    double boost = 0.0;

                    if (product != null) {
                        if (matches(preferredCategories, product.getCategoryName())) {
                            boost += categoryBoost;
                        }
                        if (matches(preferredBrands, product.getBrandName())) {
                            boost += brandBoost;
                        }
                        if (matchesAny(preferredConcerns, product.getSkinConcerns())) {
                            boost += concernBoost;
                        }
                    }

                    double newScore = Math.min(1.0, candidate.score() + boost);
                    return new ScoredCandidate(candidate.product(), newScore);
                })
                .collect(Collectors.toList());
    }

    private boolean matches(Set<String> preferred, String value) {
        if (preferred == null || preferred.isEmpty() || value == null) {
            return false;
        }
        return preferred.contains(normalize(value));
    }

    private boolean matchesAny(Set<String> preferred, List<String> values) {
        if (preferred == null || preferred.isEmpty() || values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && preferred.contains(normalize(value))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(this::normalize)
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private List<CatalogProductResponse> safeGetByCategory(UUID categoryId) {
        try {
            CatalogPageResponse<CatalogProductResponse> response = catalogProductClient.getProductsByCategory(
                    categoryId,
                    0,
                    CATALOG_PAGE_SIZE
            );
            return response == null || response.getContent() == null ? List.of() : response.getContent();
        } catch (Exception ex) {
            log.warn("Failed to load category products {}", categoryId, ex);
            return List.of();
        }
    }

    private List<CatalogProductResponse> safeGetByBrand(UUID brandId) {
        try {
            CatalogPageResponse<CatalogProductResponse> response = catalogProductClient.getProductsByBrand(
                    brandId,
                    0,
                    CATALOG_PAGE_SIZE
            );
            return response == null || response.getContent() == null ? List.of() : response.getContent();
        } catch (Exception ex) {
            log.warn("Failed to load brand products {}", brandId, ex);
            return List.of();
        }
    }
}
