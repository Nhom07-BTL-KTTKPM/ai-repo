package iuh.fit.aiservice.service;

import iuh.fit.aiservice.dto.cache.ContextMessage;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchItem;
import iuh.fit.aiservice.dto.client.CustomerProfileResponse;
import iuh.fit.aiservice.model.ProductViewLog;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    public String buildPrompt(
            ContextRetrievalService.ContextSnapshot snapshot,
            List<ContextMessage> history,
            String userMessage
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are a cosmetics assistant.\n");
        builder.append("Rules:\n");
        builder.append("- Only use the provided context.\n");
        builder.append("- Only answer questions about cosmetics, skincare, ingredients, usage, routines, and common side effects.\n");
        builder.append("- If the question is outside this scope, refuse briefly and ask for a skincare-related question.\n");
        builder.append("- If context is insufficient, say what is missing.\n");
        builder.append("- You may mention products from the provided context when relevant.\n");
        builder.append("- Do not mention product IDs.\n");
        builder.append("- When recommending products, explain the match using skin concerns, skin type, ingredients, category, brand, and usage instructions if available.\n");
        builder.append("- If no relevant product is available, still answer with practical skincare guidance and say that product suggestions are currently limited.\n");
        builder.append("- Prefer concise, practical Vietnamese.\n\n");

        builder.append("Context:\n");
        appendProfile(builder, snapshot.profile());
        appendViewLogs(builder, snapshot.viewLogs());
        appendSemanticItems(builder, snapshot.items());
        appendHistory(builder, history);

        builder.append("\nUser question:\n");
        builder.append(userMessage == null ? "" : userMessage.trim());
        builder.append("\n\nAnswer in Vietnamese. Do not invent facts. If recommending products, explain why they match.");
        return builder.toString();
    }

    private void appendProfile(StringBuilder builder, CustomerProfileResponse profile) {
        builder.append("Customer profile:\n");
        if (profile == null) {
            builder.append("- not available\n");
            return;
        }
        builder.append("- fullName: ").append(nullSafe(profile.getFullName())).append("\n");
        builder.append("- gender: ").append(nullSafe(profile.getGender())).append("\n");
        builder.append("- skinType: ").append(nullSafe(profile.getSkinType())).append("\n");
        builder.append("- skinConcerns: ").append(profile.getSkinConcerns() == null ? List.of() : profile.getSkinConcerns()).append("\n");
        builder.append("- loyaltyPoints: ").append(profile.getLoyaltyPoints()).append("\n");
    }

    private void appendViewLogs(StringBuilder builder, List<ProductViewLog> viewLogs) {
        builder.append("Recent product views:\n");
        if (viewLogs == null || viewLogs.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        for (ProductViewLog log : viewLogs) {
            builder.append("- productId: ").append(nullSafe(log.getProductId()))
                    .append(", viewedAt: ").append(log.getViewedAt())
                    .append(", source: ").append(nullSafe(log.getSource()))
                    .append("\n");
        }
    }

    private void appendSemanticItems(StringBuilder builder, List<CatalogSemanticSearchItem> items) {
        builder.append("Relevant products:\n");
        if (items == null || items.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        for (CatalogSemanticSearchItem item : items) {
            builder.append("- name: ").append(nullSafe(item.getName()))
                    .append(", category: ").append(nullSafe(item.getCategoryName()))
                    .append(", brand: ").append(nullSafe(item.getBrandName()))
                    .append(", score: ").append(item.getScore())
                    .append(", description: ").append(nullSafe(item.getDescription()))
                    .append(", ingredients: ").append(nullSafe(item.getIngredients()))
                    .append(", usageInstructions: ").append(nullSafe(item.getUsageInstructions()))
                    .append(", skinConcerns: ").append(item.getSkinConcerns())
                    .append(", suitableSkinTypes: ").append(item.getSuitableSkinTypes())
                    .append(", priceRange: ").append(item.getMinPrice()).append("-").append(item.getMaxPrice())
                    .append("\n");
        }
    }

    private void appendHistory(StringBuilder builder, List<ContextMessage> history) {
        builder.append("Chat history:\n");
        if (history == null || history.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        for (ContextMessage message : history) {
            builder.append("- ").append(message.getRole())
                    .append(": ").append(nullSafe(message.getContent()))
                    .append("\n");
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
