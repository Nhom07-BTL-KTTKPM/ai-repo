package iuh.fit.aiservice.service;

import iuh.fit.aiservice.client.CatalogServiceClient;
import iuh.fit.aiservice.client.QueryEmbeddingClient;
import iuh.fit.aiservice.client.UserServiceClient;
import iuh.fit.aiservice.config.AiRagProperties;
import iuh.fit.aiservice.dto.cache.QueryCacheEntry;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchItem;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchRequest;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchResponse;
import iuh.fit.aiservice.dto.client.CustomerProfileResponse;
import iuh.fit.aiservice.model.ProductViewLog;
import iuh.fit.aiservice.repo.ProductViewLogMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ContextRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(ContextRetrievalService.class);
    private static final int VECTOR_CANDIDATE_MULTIPLIER = 4;
    private static final int MIN_VECTOR_CANDIDATES = 20;
    private static final int MAX_VECTOR_CANDIDATES = 50;
    private static final double DEFAULT_MIN_SCORE = 0.18;
    private static final double BROAD_QUERY_MIN_SCORE = 0.12;
    private static final double FOCUSED_QUERY_MIN_SCORE = 0.18;

    private final CatalogServiceClient catalogServiceClient;
    private final UserServiceClient userServiceClient;
    private final ProductViewLogMongoRepository productViewLogRepository;
    private final AiContextCacheService cacheService;
    private final QueryEmbeddingClient queryEmbeddingClient;
    private final AiRagProperties ragProperties;

    public ContextRetrievalService(
            CatalogServiceClient catalogServiceClient,
            UserServiceClient userServiceClient,
            ProductViewLogMongoRepository productViewLogRepository,
            AiContextCacheService cacheService,
            QueryEmbeddingClient queryEmbeddingClient,
            AiRagProperties ragProperties
    ) {
        this.catalogServiceClient = catalogServiceClient;
        this.userServiceClient = userServiceClient;
        this.productViewLogRepository = productViewLogRepository;
        this.cacheService = cacheService;
        this.queryEmbeddingClient = queryEmbeddingClient;
        this.ragProperties = ragProperties;
    }


    public ContextSnapshot retrieveContext(
            String customerId,
            String query,
            Integer topKOverride
    ) {
        int topK = topKOverride == null ? ragProperties.getTopK() : topKOverride;
        CompletableFuture<CustomerProfileResponse> profileFuture = CompletableFuture.supplyAsync(
                () -> loadProfile(customerId)
        );
        CompletableFuture<List<ProductViewLog>> viewLogsFuture = CompletableFuture.supplyAsync(
                () -> loadViewLogs(customerId)
        );

        CustomerProfileResponse profile = profileFuture.join();
        List<ProductViewLog> viewLogs = viewLogsFuture.join();
        List<CatalogSemanticSearchItem> items = loadSemanticItems(customerId, query, topK, profile, viewLogs);
        return new ContextSnapshot(profile, viewLogs, items);
    }

    private CustomerProfileResponse loadProfile(String customerId) {
        try {
            var response = userServiceClient.getCustomerById(customerId);
            return response == null ? null : response.data();
        } catch (Exception ex) {
            logger.warn("Failed to load customer profile for {}", customerId, ex);
            return null;
        }
    }

    private List<ProductViewLog> loadViewLogs(String customerId) {
        try {
            return productViewLogRepository.findByCustomerIdOrderByViewedAtDesc(
                    customerId,
                    PageRequest.of(0, ragProperties.getMaxViewLogs())
            );
        } catch (Exception ex) {
            logger.warn("Failed to load product view logs for {}", customerId, ex);
            return Collections.emptyList();
        }
    }

    private List<CatalogSemanticSearchItem> loadSemanticItems(
            String customerId,
            String query,
            int topK,
            CustomerProfileResponse profile,
            List<ProductViewLog> viewLogs
    ) {
        List<QueryVectorSpec> retrievalSpecs = buildRetrievalSpecs(query, profile);
        String cacheKey = buildQueryCacheKey(customerId, retrievalSpecs, topK, profile, viewLogs);
        Optional<QueryCacheEntry> cachedEntry = cacheService.getQueryCache(cacheKey);
        if (cachedEntry.isPresent() && cachedEntry.get().getItems() != null && !cachedEntry.get().getItems().isEmpty()) {
            return cachedEntry.get().getItems();
        }

        List<CatalogSemanticSearchItem> items = runVectorRetrieval(customerId, topK, retrievalSpecs);

        List<String> productIds = items.stream()
                .map(item -> item.getProductId() == null ? null : item.getProductId().toString())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        QueryCacheEntry entry = QueryCacheEntry.builder()
                .productIds(productIds)
                .items(items)
                .cachedAt(Instant.now())
                .build();
        cacheService.saveQueryCache(cacheKey, entry);
        return items;
    }

    private String buildQueryCacheKey(
            String customerId,
            List<QueryVectorSpec> retrievalSpecs,
            int topK,
            CustomerProfileResponse profile,
            List<ProductViewLog> viewLogs
    ) {
        String normalizedSpecs = retrievalSpecs.stream()
                .map(spec -> spec.text() + "@" + spec.weight() + "@" + spec.minScore())
                .collect(Collectors.joining("||"));
        String normalized = normalizedSpecs
                + "|" + topK
                + "|" + buildContextSignature(profile, viewLogs);
        String hash = sha256(normalized);
        return String.format("ai:query:%s:%s", customerId, hash);
    }

    private String buildRetrievalText(String query, CustomerProfileResponse profile) {
        StringBuilder builder = new StringBuilder();
        String trimmedQuery = query == null ? "" : query.trim();
        if (!trimmedQuery.isEmpty()) {
            builder.append(trimmedQuery);
        }
        if (profile != null) {
            boolean hasSkinType = profile.getSkinType() != null && !profile.getSkinType().isBlank();
            boolean hasConcerns = profile.getSkinConcerns() != null && !profile.getSkinConcerns().isEmpty();
            if (hasSkinType || hasConcerns) {
                if (builder.length() > 0) {
                    builder.append(". ");
                }
                builder.append("Phu hop cho khach hang: ");
                if (hasSkinType) {
                    builder.append("da ").append(profile.getSkinType().trim().toLowerCase());
                }
                if (hasConcerns) {
                    if (hasSkinType) {
                        builder.append(", ");
                    }
                    String concerns = profile.getSkinConcerns().stream()
                            .filter(value -> value != null && !value.isBlank())
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .collect(Collectors.joining(", "));
                    builder.append("dang gap van de ").append(concerns);
                }
            }
        }
        return builder.toString().trim();
    }

    private List<QueryVectorSpec> buildRetrievalSpecs(
            String query,
            CustomerProfileResponse profile
    ) {
        String baseText = buildRetrievalText(query, profile);
        if (baseText.isBlank()) {
            return List.of();
        }

        List<QueryVectorSpec> specs = new ArrayList<>();
        specs.add(new QueryVectorSpec(baseText, baseText, 1.0, DEFAULT_MIN_SCORE));

        return specs;
    }

    private List<CatalogSemanticSearchItem> runVectorRetrieval(
            String customerId,
            int topK,
            List<QueryVectorSpec> retrievalSpecs
    ) {
        if (retrievalSpecs == null || retrievalSpecs.isEmpty()) {
            return List.of();
        }

        int candidateTopK = Math.min(MAX_VECTOR_CANDIDATES, Math.max(topK * VECTOR_CANDIDATE_MULTIPLIER, MIN_VECTOR_CANDIDATES));
        LinkedHashMap<String, CatalogSemanticSearchItem> merged = new LinkedHashMap<>();

        for (QueryVectorSpec spec : retrievalSpecs) {
            List<Double> embedding;
            try {
                embedding = queryEmbeddingClient.embed(spec.text());
            } catch (Exception ex) {
                logger.warn("Failed to generate embedding for customer {} and query [{}]", customerId, spec.text(), ex);
                continue;
            }
            if (embedding == null || embedding.isEmpty()) {
                continue;
            }

            CatalogSemanticSearchRequest request = CatalogSemanticSearchRequest.builder()
                    .embedding(embedding)
                    .topK(candidateTopK)
                    .minScore(spec.minScore())
                    .queryText(spec.queryText())
                    .build();

            CatalogSemanticSearchResponse response;
            try {
                response = catalogServiceClient.semanticSearch(request);
            } catch (Exception ex) {
                logger.warn("Failed semantic search in catalog-service for customer {} and hint [{}]", customerId, spec.queryText(), ex);
                continue;
            }

            List<CatalogSemanticSearchItem> items = response == null || response.getItems() == null
                    ? List.of()
                    : response.getItems();

            for (CatalogSemanticSearchItem item : items) {
                putCandidate(merged, reweighted(item, spec.weight()));
            }
        }

        return new ArrayList<>(merged.values());
    }

    private CatalogSemanticSearchItem reweighted(CatalogSemanticSearchItem item, double weight) {
        if (item == null) {
            return null;
        }
        Double score = item.getScore();
        return CatalogSemanticSearchItem.builder()
                .productId(item.getProductId())
                .name(item.getName())
                .description(item.getDescription())
                .ingredients(item.getIngredients())
                .usageInstructions(item.getUsageInstructions())
                .categoryName(item.getCategoryName())
                .brandName(item.getBrandName())
                .suitableSkinTypes(item.getSuitableSkinTypes())
                .skinConcerns(item.getSkinConcerns())
                .minPrice(item.getMinPrice())
                .maxPrice(item.getMaxPrice())
                .score(score == null ? null : score * weight)
                .build();
    }

    private void putCandidate(LinkedHashMap<String, CatalogSemanticSearchItem> merged, CatalogSemanticSearchItem item) {
        if (item == null || item.getProductId() == null) {
            return;
        }
        String key = item.getProductId().toString();
        CatalogSemanticSearchItem existing = merged.get(key);
        if (existing == null) {
            merged.put(key, item);
            return;
        }
        Double existingScore = existing.getScore();
        Double incomingScore = item.getScore();
        if (incomingScore != null && (existingScore == null || incomingScore > existingScore)) {
            merged.put(key, item);
        }
    }

    private String buildContextSignature(CustomerProfileResponse profile, List<ProductViewLog> viewLogs) {
        StringBuilder builder = new StringBuilder();
        if (profile != null) {
            builder.append(profile.getSkinType()).append('|');
            if (profile.getSkinConcerns() != null) {
                builder.append(String.join(",", profile.getSkinConcerns()));
            }
        }
        builder.append('|');
        if (viewLogs != null) {
            viewLogs.stream()
                    .limit(5)
                    .forEach(log -> builder.append(log.getProductId())
                            .append('@')
                            .append(log.getViewedAt())
                            .append('|'));
        }
        return builder.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record ContextSnapshot(
            CustomerProfileResponse profile,
            List<ProductViewLog> viewLogs,
            List<CatalogSemanticSearchItem> items
    ) {
    }

    private record QueryVectorSpec(String text, String queryText, double weight, double minScore) {
    }
}
