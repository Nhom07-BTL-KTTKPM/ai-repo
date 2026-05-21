package iuh.fit.aiservice.recommendation.service;

import iuh.fit.aiservice.dto.client.CustomerProfileResponse;
import iuh.fit.aiservice.recommendation.client.dto.CatalogProductResponse;
import iuh.fit.aiservice.recommendation.config.RecommendationProperties;
import iuh.fit.aiservice.recommendation.ollama.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecommendationExplanationService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationExplanationService.class);

    private final OllamaClient ollamaClient;
    private final RecommendationProperties properties;

    public RecommendationExplanationService(
            OllamaClient ollamaClient,
            RecommendationProperties properties
    ) {
        this.ollamaClient = ollamaClient;
        this.properties = properties;
    }

    public String generateReason(CustomerProfileResponse profile, CatalogProductResponse product) {
        if (!properties.isAiReasonEnabled()) {
            return fallbackReason(profile, product);
        }

        String prompt = buildPrompt(profile, product);
        try {
            String response = ollamaClient.generate(prompt);
            if (response != null && !response.isBlank()) {
                return response;
            }
        } catch (Exception ex) {
            logger.warn("Ollama reason generation failed", ex);
        }
        return fallbackReason(profile, product);
    }

    private String buildPrompt(CustomerProfileResponse profile, CatalogProductResponse product) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are a beauty assistant. ");
        builder.append("Generate a short explanation in Vietnamese, max 30 words. ");
        builder.append("Do not mention internal scoring.\n");

        builder.append("User skin type: ").append(nullSafe(profile == null ? null : profile.getSkinType())).append("\n");
        builder.append("User concerns: ").append(toCsv(profile == null ? null : profile.getSkinConcerns())).append("\n");
        builder.append("Recommended product: ").append(nullSafe(product.getName())).append("\n");
        builder.append("Product skin types: ").append(toCsv(product.getSuitableSkinTypes())).append("\n");
        builder.append("Product concerns: ").append(toCsv(product.getSkinConcerns())).append("\n");
        builder.append("Key ingredients: ").append(trim(product.getIngredients(), 200)).append("\n");
        return builder.toString();
    }

    private String fallbackReason(CustomerProfileResponse profile, CatalogProductResponse product) {
        String skinType = profile == null ? null : profile.getSkinType();
        String name = product == null ? null : product.getName();
        String brand = product == null ? null : product.getBrandName();
        String category = product == null ? null : product.getCategoryName();
        if (skinType != null && name != null && !skinType.isBlank()) {
            return "Phu hop voi da " + skinType + ", uu tien tu hanh vi tuong tac gan day.";
        }
        if (brand != null && !brand.isBlank() && category != null && !category.isBlank()) {
            return "Goi y tu nhom " + category + " va thuong hieu " + brand + " ban quan tam.";
        }
        if (category != null && !category.isBlank()) {
            return "Goi y dua tren hanh vi tuong tac gan day voi nhom " + category + ".";
        }
        return "Goi y dua tren hanh vi tuong tac gan day.";
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String toCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.joining(", "));
    }

    private String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
