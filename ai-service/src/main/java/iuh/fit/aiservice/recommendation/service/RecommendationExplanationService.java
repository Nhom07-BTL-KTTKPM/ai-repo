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
            String response = ollamaClient.generate(prompt, "json");
            if (response != null && !response.isBlank()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
                    if (root.has("reason") && !root.get("reason").isNull()) {
                        String parsedReason = root.get("reason").asText();
                        if (!parsedReason.isBlank()) {
                            return parsedReason;
                        }
                    }
                } catch (Exception parseEx) {
                    logger.warn("Failed to parse JSON reason from Ollama", parseEx);
                }
            }
        } catch (Exception ex) {
            logger.warn("Ollama reason generation failed", ex);
        }
        return fallbackReason(profile, product);
    }

    private String buildPrompt(CustomerProfileResponse profile, CatalogProductResponse product) {
        StringBuilder builder = new StringBuilder();
        builder.append("Bạn là chuyên gia tư vấn sắc đẹp và bán hàng. ");
        builder.append("Viết MỘT CÂU QUẢNG CÁO (TỐI ĐA 25 TỪ) bằng Tiếng Việt, ngắn gọn, thuyết phục.\n");
        builder.append("Trả về JSON: {\"reason\": \"<câu tư vấn>\"}\n\n");

        // --- Thông tin sản phẩm ---
        String productName = nullSafe(product.getName());
        String categoryName = nullSafe(product.getCategoryName());
        String productConcerns = toCsv(product.getSkinConcerns());
        String productSkinTypes = toCsv(product.getSuitableSkinTypes());
        String ingredients = trim(product.getIngredients(), 200);
        boolean isHairBody = isHairOrBodyProduct(categoryName);

        builder.append("Sản phẩm: ").append(productName).append("\n");
        builder.append("Danh mục: ").append(categoryName).append("\n");
        builder.append("Thành phần chính: ").append(ingredients).append("\n");

        // --- Logic phân nhánh dựa vào profile người dùng ---
        String userSkinType = profile == null ? null : nullSafe(profile.getSkinType());
        String userConcerns = profile == null ? null : toCsv(profile.getSkinConcerns());
        boolean hasUserProfile = userSkinType != null && !userSkinType.isBlank();

        if (isHairBody) {
            // Sản phẩm tóc/thân thể: không liên quan đến loại da, tập trung vào tính năng sản phẩm
            builder.append("\nLưu ý: Đây là sản phẩm chăm sóc tóc/thân thể, KHÔNG phải mỹ phẩm cho da mặt.\n");
            builder.append("Nhiệm vụ: Quảng cáo tính năng nổi bật của sản phẩm (").append(productConcerns).append(").\n");
            builder.append("Dùng cụm từ thúc đẩy: 'Chắc chắn phải thử', 'Hiệu quả rõ rệt', hoặc 'Tuyệt vời cho tóc'.\n");
        } else if (!hasUserProfile) {
            // Không có thông tin da người dùng: quảng cáo tính năng nổi bật của sản phẩm
            builder.append("\nKhách hàng chưa cung cấp thông tin loại da.\n");
            builder.append("Công dụng sản phẩm: ").append(productConcerns).append("\n");
            builder.append("Loại da phù hợp: ").append(productSkinTypes).append("\n");
            builder.append("Nhiệm vụ: Quảng cáo tính năng nổi bật và đối tượng phù hợp của sản phẩm.\n");
            builder.append("Dùng cụm từ: 'Rất nên mua', 'Phù hợp với bạn', hoặc 'Đáng trải nghiệm'.\n");
        } else {
            // Có đủ thông tin da người dùng: so sánh và tư vấn có chiều sâu
            builder.append("\nThông tin khách hàng:\n");
            builder.append("  - Loại da: ").append(userSkinType).append("\n");
            builder.append("  - Vấn đề da: ").append(userConcerns).append("\n");
            builder.append("Sản phẩm phù hợp với loại da: ").append(productSkinTypes).append("\n");
            builder.append("Sản phẩm giải quyết vấn đề: ").append(productConcerns).append("\n");

            boolean skinTypeMatch = productSkinTypes.isBlank() ||
                    productSkinTypes.toLowerCase().contains(userSkinType.toLowerCase()) ||
                    productSkinTypes.toLowerCase().contains("all") ||
                    productSkinTypes.toLowerCase().contains("tất cả");

            if (skinTypeMatch) {
                builder.append("Phân tích: Sản phẩm PHÙ HỢP với loại da của khách hàng.\n");
                builder.append("Nhiệm vụ: So sánh vấn đề da của khách hàng với công dụng sản phẩm, ");
                builder.append("chỉ rõ thành phần nào trong sản phẩm giúp giải quyết đúng vấn đề đó.\n");
                builder.append("Dùng cụm từ thúc đẩy mạnh: 'Rất nên mua ngay', 'Hoàn toàn phù hợp với bạn', 'Chắc chắn hiệu quả'.\n");
            } else {
                builder.append("Phân tích: Sản phẩm KHÔNG được thiết kế chuyên biệt cho loại da này.\n");
                builder.append("Nhiệm vụ: Quảng cáo tính năng và thành phần nổi bật của sản phẩm, ");
                builder.append("không nhắc đến loại da không phù hợp.\n");
                builder.append("Dùng cụm từ: 'Đáng khám phá', 'Thành phần độc đáo', 'Hiệu quả ấn tượng'.\n");
            }
        }

        return builder.toString();
    }

    private boolean isHairOrBodyProduct(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return false;
        String lower = categoryName.toLowerCase();
        return lower.contains("hair") || lower.contains("shampoo") || lower.contains("conditioner")
                || lower.contains("tóc") || lower.contains("body") || lower.contains("thân thể")
                || lower.contains("bodycare") || lower.contains("haircare");
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
