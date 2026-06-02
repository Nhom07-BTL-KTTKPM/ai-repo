package iuh.fit.aiservice.recommendation.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.aiservice.dto.client.CustomerProfileResponse;
import iuh.fit.aiservice.recommendation.client.dto.CatalogProductResponse;
import iuh.fit.aiservice.recommendation.config.RecommendationProperties;
import iuh.fit.aiservice.recommendation.ollama.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PreferenceInsightService {

    private static final Logger logger = LoggerFactory.getLogger(PreferenceInsightService.class);

    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;

    public PreferenceInsightService(
            OllamaClient ollamaClient,
            ObjectMapper objectMapper,
            RecommendationProperties properties
    ) {
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public PreferenceInsight inferPreferences(
            CustomerProfileResponse profile,
            List<CatalogProductResponse> recentProducts
    ) {
        if (!properties.isAiPreferenceEnabled()) {
            return null;
        }

        if (recentProducts == null || recentProducts.isEmpty()) {
            return null;
        }

        List<String> categories = uniqueValues(recentProducts.stream()
                .map(CatalogProductResponse::getCategoryName)
                .collect(Collectors.toList()), 8);
        List<String> brands = uniqueValues(recentProducts.stream()
                .map(CatalogProductResponse::getBrandName)
                .collect(Collectors.toList()), 8);
        List<String> concerns = profile == null ? List.of() : safeList(profile.getSkinConcerns());

        String prompt = buildPrompt(profile == null ? null : profile.getSkinType(), categories, brands, concerns);
        try {
            String response = ollamaClient.generate(prompt, "json");
            if (response == null || response.isBlank()) {
                return null;
            }
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start != -1 && end != -1 && start <= end) {
                String jsonContent = response.substring(start, end + 1);
                return objectMapper.readValue(jsonContent, PreferenceInsight.class);
            }
            return objectMapper.readValue(response, PreferenceInsight.class);
        } catch (Exception ex) {
            logger.warn("Preference insight parse failed", ex);
            return null;
        }
    }

    private String buildPrompt(
            String skinType,
            List<String> categories,
            List<String> brands,
            List<String> concerns
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("/no_think\n");
        builder.append("You analyze user preferences for skincare products. ");
        builder.append("Return ONLY valid JSON with keys: preferredCategories, preferredBrands, preferredConcerns. ");
        builder.append("Choose ONLY from the provided lists. ");
        builder.append("If none, return empty arrays.\n");
        builder.append("skinType: ").append(safeString(skinType)).append("\n");
        builder.append("categories: ").append(categories).append("\n");
        builder.append("brands: ").append(brands).append("\n");
        builder.append("concerns: ").append(concerns).append("\n");
        return builder.toString();
    }

    private List<String> uniqueValues(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                unique.add(value.trim());
            }
        }
        List<String> result = new ArrayList<>(unique);
        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreferenceInsight(
            List<String> preferredCategories,
            List<String> preferredBrands,
            List<String> preferredConcerns
    ) {
    }
}
