package iuh.fit.aiservice.service;

import iuh.fit.aiservice.client.CatalogServiceClient;
import iuh.fit.aiservice.client.GeminiClient;
import iuh.fit.aiservice.client.UserServiceClient;
import iuh.fit.aiservice.config.AiRagProperties;
import iuh.fit.aiservice.dto.cache.QueryCacheEntry;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchItem;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchRequest;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchResponse;
import iuh.fit.aiservice.dto.client.CustomerProfileResponse;
import iuh.fit.aiservice.model.ProductViewLog;
import iuh.fit.aiservice.repo.ProductViewLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ContextRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(ContextRetrievalService.class);

    private final CatalogServiceClient catalogServiceClient;
    private final UserServiceClient userServiceClient;
    private final ProductViewLogRepository productViewLogRepository;
    private final AiContextCacheService cacheService;
    private final GeminiClient geminiClient;
    private final AiRagProperties ragProperties;

    public ContextRetrievalService(
            CatalogServiceClient catalogServiceClient,
            UserServiceClient userServiceClient,
            ProductViewLogRepository productViewLogRepository,
            AiContextCacheService cacheService,
            GeminiClient geminiClient,
            AiRagProperties ragProperties
    ) {
        this.catalogServiceClient = catalogServiceClient;
        this.userServiceClient = userServiceClient;
        this.productViewLogRepository = productViewLogRepository;
        this.cacheService = cacheService;
        this.geminiClient = geminiClient;
        this.ragProperties = ragProperties;
    }

    public ContextSnapshot retrieveContext(String customerId, String query, Integer topKOverride) {
        int topK = topKOverride == null ? ragProperties.getTopK() : topKOverride;
        CustomerProfileResponse profile = loadProfile(customerId);
        List<ProductViewLog> viewLogs = loadViewLogs(customerId);
        List<CatalogSemanticSearchItem> items = loadSemanticItems(customerId, query, topK);
        return new ContextSnapshot(profile, viewLogs, items);
    }

    private CustomerProfileResponse loadProfile(String customerId) {
        try {
            return userServiceClient.getCustomerById(customerId);
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

    private List<CatalogSemanticSearchItem> loadSemanticItems(String customerId, String query, int topK) {
        String cacheKey = buildQueryCacheKey(customerId, query);
        Optional<QueryCacheEntry> cachedEntry = cacheService.getQueryCache(cacheKey);
        if (cachedEntry.isPresent() && cachedEntry.get().getItems() != null && !cachedEntry.get().getItems().isEmpty()) {
            return cachedEntry.get().getItems();
        }

        List<Double> embedding = geminiClient.embedContent(query).values();
        if (embedding == null || embedding.isEmpty()) {
            return List.of();
        }
        CatalogSemanticSearchRequest request = CatalogSemanticSearchRequest.builder()
                .embedding(embedding)
                .topK(topK)
                .build();
        CatalogSemanticSearchResponse response = catalogServiceClient.semanticSearch(request);
        List<CatalogSemanticSearchItem> items = response == null || response.getItems() == null
                ? List.of()
                : response.getItems();

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

    private String buildQueryCacheKey(String customerId, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        String hash = sha256(normalized);
        return String.format("ai:query:%s:%s", customerId, hash);
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
}
