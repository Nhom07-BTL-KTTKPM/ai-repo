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
import java.text.Normalizer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
@Service
public class ContextRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(ContextRetrievalService.class);
    private static final int VECTOR_CANDIDATE_MULTIPLIER = 4;
    private static final int MIN_VECTOR_CANDIDATES = 20;
    private static final int MAX_VECTOR_CANDIDATES = 50;
    private static final double DEFAULT_MIN_SCORE = 0.30;
    private static final double BROAD_QUERY_MIN_SCORE = 0.12;
    private static final double FOCUSED_QUERY_MIN_SCORE = 0.18;
    private static final Map<String, List<String>> PRODUCT_TYPE_ALIASES = Map.ofEntries(
        // Các mục gốc
        Map.entry("serum", List.of("serum", "tinh chat", "essence", "ampoule", "concentrate")),
        Map.entry("sunscreen", List.of("sunscreen", "sunblock", "kem chong nang", "chong nang")),
        Map.entry("cleanser", List.of("cleanser", "face wash", "foam cleanser", "sua rua mat", "rua mat")),
        Map.entry("moisturizer", List.of("moisturizer", "hydrating cream", "kem duong", "duong am")),
        Map.entry("toner", List.of("toner", "balancing toner", "nuoc can bang")),
        Map.entry("mask", List.of("mask", "mat na")),
        Map.entry("shampoo", List.of("shampoo", "dau goi", "goi dau", "toc", "cham soc toc", "hair", "haircare")),
        Map.entry("gel", List.of("gel")),
        Map.entry("cream", List.of("cream", "kem")),
        Map.entry("makeup_remover", List.of("makeup remover", "tay trang", "nuoc tay trang", "dau tay trang", "sap tay trang", "micellar water")),
        Map.entry("exfoliator", List.of("exfoliator", "tay te bao chet", "tay da chet", "bha", "aha", "pha", "peel")),
        Map.entry("treatment", List.of("treatment", "dac tri", "cham mun", "tri mun", "retinol", "tretinoin")),
        Map.entry("bodycare", List.of("bodycare", "sua tam", "kem tuyet", "duong the", "body lotion", "body wash")),
        Map.entry("makeup", List.of("makeup", "trang diem")),

        // Mở rộng
        Map.entry("hair_conditioner", List.of("conditioner", "dau xa", "xa toc", "kem xa")),
        Map.entry("hair_mask", List.of("hair mask", "kem u toc", "mat na toc")),
        Map.entry("hair_oil", List.of("hair oil", "dau duong toc", "serum toc")),
        Map.entry("scalp_treatment", List.of("scalp treatment", "dieu tri da dau", "cham soc da dau")),
        Map.entry("body_lotion", List.of("body lotion", "sua duong the", "kem duong the", "body cream")),
        Map.entry("hand_cream", List.of("hand cream", "kem duong tay")),
        Map.entry("foot_cream", List.of("foot cream", "kem duong chan")),
        Map.entry("lip_balm", List.of("lip balm", "son duong", "duong moi")),
        Map.entry("eye_cream", List.of("eye cream", "kem mat", "duong mat")),
        Map.entry("face_oil", List.of("face oil", "dau duong mat", "facial oil")),
        Map.entry("mist", List.of("mist", "xit khoang", "facial mist", "setting spray")),
        Map.entry("ampoule", List.of("ampoule", "ampule")),
        Map.entry("essence", List.of("essence", "nuoc than", "first essence")),
        Map.entry("emulsion", List.of("emulsion", "nhu tuong")),
        Map.entry("sheet_mask", List.of("sheet mask", "mat na giay")),
        Map.entry("sleeping_mask", List.of("sleeping mask", "mat na ngu")),
        Map.entry("wash_off_mask", List.of("wash off mask", "mat na rua", "mat na dat set")),
        Map.entry("sunscreen_stick", List.of("sunscreen stick", "son chong nang", "chong nang dang thoi")),
        Map.entry("sun_cushion", List.of("sun cushion", "cushion chong nang")),
        Map.entry("primer", List.of("primer", "kem lot", "base makeup")),
        Map.entry("foundation", List.of("foundation", "kem nen", "cushion", "phan nuoc")),
        Map.entry("concealer", List.of("concealer", "che khuyet diem", "kem che khuyet diem")),
        Map.entry("powder", List.of("powder", "phan phu", "phan bot", "loose powder", "pressed powder")),
        Map.entry("blush", List.of("blush", "ma hong")),
        Map.entry("highlighter", List.of("highlighter", "phan bat sang", "tao khoi sang")),
        Map.entry("contour", List.of("contour", "tao khoi", "phan tao khoi")),
        Map.entry("eyeshadow", List.of("eyeshadow", "phan mat")),
        Map.entry("eyeliner", List.of("eyeliner", "ke mat", "but ke mat")),
        Map.entry("mascara", List.of("mascara", "mascara")),
        Map.entry("eyebrow", List.of("eyebrow", "chi ke may", "bot ke may", "gel ke may", "ke chan may")),
        Map.entry("lipstick", List.of("lipstick", "son moi", "son thoi", "son kem")),
        Map.entry("lip_tint", List.of("lip tint", "son tint", "son nuoc")),
        Map.entry("lip_gloss", List.of("lip gloss", "son bong")),
        Map.entry("lip_liner", List.of("lip liner", "chi ke vien moi")),
        Map.entry("setting_spray", List.of("setting spray", "xit khoa makeup", "xit co dinh makeup")),
        Map.entry("nail_polish", List.of("nail polish", "son mong tay")),
        Map.entry("perfume", List.of("perfume", "nuoc hoa", "eau de parfum", "eau de toilette", "cologne")),
        Map.entry("deodorant", List.of("deodorant", "lan khu mui", "xit khu mui"))
    );

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

    public ContextSnapshot retrieveContext(String customerId, String query, Integer topKOverride) {
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

        List<CatalogSemanticSearchItem> items = runVectorRetrieval(customerId, query, topK, retrievalSpecs);

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
                builder.append("Phù hợp cho khách hàng: ");
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
                    builder.append("đang gặp vấn đề ").append(concerns);
                }
            }
        }
        return builder.toString().trim();
    }

    private List<QueryVectorSpec> buildRetrievalSpecs(String query, CustomerProfileResponse profile) {
        String baseText = buildRetrievalText(query, profile);
        if (baseText.isBlank()) {
            return List.of();
        }

        List<QueryVectorSpec> specs = new ArrayList<>();
        specs.add(new QueryVectorSpec(baseText, 1.0, DEFAULT_MIN_SCORE));

        String normalizedQuery = normalize(query);
        String brandHint = extractBrandHint(normalizedQuery);
        if (brandHint != null && !brandHint.isBlank()) {
            specs.add(new QueryVectorSpec(
                    "Sản phẩm thuộc thương hiệu " + brandHint + ". " + baseText,
                    0.92,
                    FOCUSED_QUERY_MIN_SCORE
            ));
        }

        String productType = extractProductTypeHint(normalizedQuery);
        if (productType != null && !productType.isBlank()) {
            List<String> aliases = PRODUCT_TYPE_ALIASES.getOrDefault(productType, List.of(productType));
            specs.add(new QueryVectorSpec(
                    "Loại sản phẩm: " + String.join(", ", aliases) + ". " + baseText,
                    0.90,
                    BROAD_QUERY_MIN_SCORE
            ));
        }

        return specs;
    }

    private List<CatalogSemanticSearchItem> runVectorRetrieval(
            String customerId,
            String query,
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
                logger.warn("Failed to generate embedding for customer {} and query [{}]", customerId, query, ex);
                continue;
            }
            if (embedding == null || embedding.isEmpty()) {
                continue;
            }

            CatalogSemanticSearchRequest request = CatalogSemanticSearchRequest.builder()
                    .embedding(embedding)
                    .topK(candidateTopK)
                    .minScore(spec.minScore())
                    .queryText(query)
                    .build();
            CatalogSemanticSearchResponse response;
            try {
                response = catalogServiceClient.semanticSearch(request);
            } catch (Exception ex) {
                logger.warn("Failed semantic search in catalog-service for customer {} and query [{}]", customerId, query, ex);
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

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
        String result = normalized.replaceAll("\\s+", " ");
        result = result.replace("cevare", "cerave");
        result = result.replace("inisfree", "innisfree");
        return result;
    }

    private String extractBrandHint(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return null;
        }
        List<String> tokens = List.of(normalizedQuery.split("\\s+"));
        int brandMarkerIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("hang") || token.equals("brand") || token.equals("thuong") || token.equals("hieu") || token.equals("cua")) {
                brandMarkerIndex = i;
            }
        }
        if (brandMarkerIndex >= 0 && brandMarkerIndex + 1 < tokens.size()) {
            return tokens.get(brandMarkerIndex + 1);
        }
        return null;
    }

    private String extractProductTypeHint(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return null;
        }
        String bestType = null;
        int bestAliasLength = 0;
        for (Map.Entry<String, List<String>> entry : PRODUCT_TYPE_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (!alias.isBlank() && normalizedQuery.contains(alias) && alias.length() > bestAliasLength) {
                    bestType = entry.getKey();
                    bestAliasLength = alias.length();
                }
            }
        }
        return bestType;
    }

    public record ContextSnapshot(
            CustomerProfileResponse profile,
            List<ProductViewLog> viewLogs,
            List<CatalogSemanticSearchItem> items
    ) {
    }

    private record QueryVectorSpec(String text, double weight, double minScore) {
    }
}
