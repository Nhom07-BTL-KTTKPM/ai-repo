package iuh.fit.aiservice.recommendation.engine;

import iuh.fit.aiservice.recommendation.client.dto.CatalogProductResponse;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RecommendationEngine {

    public List<ScoredCandidate> scoreCandidates(List<CatalogProductResponse> candidates, RecommendationContext context) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.getId() != null)
                .map(candidate -> new ScoredCandidate(candidate, score(candidate, context)))
                .collect(Collectors.toList());
    }

    private double score(CatalogProductResponse candidate, RecommendationContext context) {
        double score = 0.0;

        score += weightedCategoryScore(candidate, context) * 0.4;
        score += weightedBrandScore(candidate, context) * 0.2;

        if (matchesSkinType(context.skinType(), candidate.getSuitableSkinTypes())) {
            score += 0.2;
        }

        if (matchesConcerns(context.skinConcerns(), candidate.getSkinConcerns())) {
            score += 0.2;
        }

        return score;
    }

    private boolean matchesSkinType(String skinType, List<String> suitableSkinTypes) {
        if (skinType == null || skinType.isBlank() || suitableSkinTypes == null) {
            return false;
        }
        String normalized = normalize(skinType);
        return suitableSkinTypes.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .anyMatch(normalized::equals);
    }

    private boolean matchesConcerns(Set<String> concerns, List<String> candidateConcerns) {
        if (concerns == null || concerns.isEmpty() || candidateConcerns == null) {
            return false;
        }
        Set<String> normalized = candidateConcerns.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .collect(Collectors.toSet());
        for (String concern : concerns) {
            if (normalized.contains(normalize(concern))) {
                return true;
            }
        }
        return false;
    }

    private double weightedCategoryScore(CatalogProductResponse candidate, RecommendationContext context) {
        if (candidate.getCategoryId() == null) {
            return 0.0;
        }
        Double weight = context.categoryWeights().get(candidate.getCategoryId());
        if (weight == null || weight <= 0.0 || context.maxCategoryWeight() <= 0.0) {
            return 0.0;
        }
        return normalize(weight, context.maxCategoryWeight());
    }

    private double weightedBrandScore(CatalogProductResponse candidate, RecommendationContext context) {
        if (candidate.getBrandId() == null) {
            return 0.0;
        }
        Double weight = context.brandWeights().get(candidate.getBrandId());
        if (weight == null || weight <= 0.0 || context.maxBrandWeight() <= 0.0) {
            return 0.0;
        }
        return normalize(weight, context.maxBrandWeight());
    }

    private double normalize(double value, double max) {
        if (max <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, value / max);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record RecommendationContext(
            Map<UUID, Double> categoryWeights,
            Map<UUID, Double> brandWeights,
            double maxCategoryWeight,
            double maxBrandWeight,
            String skinType,
            Set<String> skinConcerns,
            Set<UUID> viewedProductIds
    ) {
        public RecommendationContext {
            categoryWeights = safeMap(categoryWeights);
            brandWeights = safeMap(brandWeights);
            skinConcerns = skinConcerns == null ? new HashSet<>() : skinConcerns;
            viewedProductIds = safeSet(viewedProductIds);
        }

        private static <T> Set<T> safeSet(Set<T> input) {
            return input == null ? new HashSet<>() : input;
        }

        private static <K, V> Map<K, V> safeMap(Map<K, V> input) {
            return input == null ? new HashMap<>() : input;
        }
    }

    public record ScoredCandidate(CatalogProductResponse product, double score) {
    }
}
