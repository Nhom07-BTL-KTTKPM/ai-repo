package iuh.fit.aiservice.service;

import iuh.fit.aiservice.dto.cache.ContextMessage;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchItem;
import iuh.fit.aiservice.dto.client.CustomerProfileResponse;
import iuh.fit.aiservice.model.ProductViewLog;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    public String buildUnifiedPrompt(
            ContextRetrievalService.ContextSnapshot snapshot,
            String userMessage
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Ban la chuyen gia tu van my pham cua cua hang cosmetics platform. Tra loi bang tieng Viet.\n");
        builder.append("QUY TAC BAT BUOC:\n");
        builder.append("- Neu cau hoi khong lien quan den my pham/skincare/lam dep: in_scope = false.\n");
        builder.append("- Neu cau hoi nhay cam ve di ung/tac dung phu/ba bau: is_safety_sensitive = true.\n");
        builder.append("- KIEM TRA TRUNG KHOP TEN SAN PHAM:\n");
        builder.append("  + Khi nguoi dung hoi ve mot san pham cu the, hay so sanh ky ten san pham do voi cac ung vien.\n");
        builder.append("  + Vi du: Nguoi dung hoi \"Paula's Choice Skin Recovery Daily Moisturizing Lotion\" nhung ung vien chi co \"Paula's Choice Clear Ultra-Light Daily Hydrating Fluid SPF30+\" -> Day la hai san pham KHAC NHAU (dong 'Skin Recovery' va dong 'Clear' khac nhau). Do do, san pham nguoi dung hoi la KHONG CO trong danh sach ung vien.\n");
        builder.append("  + Neu ten san pham khach hoi KHONG co mat hoac khong khop trong danh sach ung vien, ban PHAI viet ro trong reply: 'San pham [Ten sp khach hoi] hien tai khong co san hoac da het hang tai cua hang.' sau do moi goi y san pham thay the tu danh sach ung vien.\n");
        builder.append("- Co 3 loai cau hoi chinh:\n");
        builder.append("  1. Goi y san pham (recommendation_intent = true): chon toi da 3 san pham phu hop nhat tu danh sach, moi san pham viet 1-2 cau ly do ngan gon.\n");
        builder.append("  2. Hoi thong tin cu the ve mot san pham (gia, con hang, thanh phan, cach dung...):\n");
        builder.append("     + Neu san pham do CO trong danh sach ung vien: recommendation_intent = false, PHAI dat UUID san pham do vao selected_product_ids VA nhung vao cuoi reply theo dang [**uuid**], tra loi dua tren du lieu san pham.\n");
        builder.append("     + Neu san pham do KHONG CO trong danh sach ung vien (het hang/ngung ban): recommendation_intent = true, trong reply phai tuyen bo ro san pham do hien tai het hang/ngung ban/khong co san tai cua hang, dong thoi chu dung goi y 1-2 san pham thay the phu hop tu danh sach ung vien, dua UUID cua san pham thay the do vao selected_product_ids va nhung vao cuoi reply theo dang [**uuid**].\n");
        builder.append("  3. Cau hoi kien thuc skincare thuan tuy (khong hoi ve san pham cu the): recommendation_intent = false, selected_product_ids = [], tra loi ngan gon 2-3 cau.\n");
        builder.append("- CHI tra ve mot doi tuong JSON hop le, KHONG them chu nao khac ngoai JSON.\n\n");
        builder.append("Cau truc JSON tra ve:\n");
        builder.append("{\"in_scope\": boolean, \"is_safety_sensitive\": boolean, \"recommendation_intent\": boolean, \"selected_product_ids\": [\"uuid\",...], \"reply\": \"noi dung\"}\n\n");

        appendProfile(builder, snapshot.profile());

        builder.append("Danh sach san pham ung vien:\n");
        if (snapshot.items() == null || snapshot.items().isEmpty()) {
            builder.append("- Khong co san pham nao phu hop trong kho\n");
        } else {
            for (CatalogSemanticSearchItem item : snapshot.items()) {
                if (item == null || item.getProductId() == null || item.getName() == null) {
                    continue;
                }
                builder.append("- ID: ").append(item.getProductId().toString()).append("\n");
                builder.append("  Ten: ").append(item.getName().trim()).append("\n");
                if (item.getBrandName() != null) builder.append("  Thuong hieu: ").append(item.getBrandName().trim()).append("\n");
                if (item.getCategoryName() != null) builder.append("  Nhom: ").append(item.getCategoryName().trim()).append("\n");
                if (item.getSuitableSkinTypes() != null && !item.getSuitableSkinTypes().isEmpty()) {
                    builder.append("  Phu hop da: ").append(String.join(", ", item.getSuitableSkinTypes())).append("\n");
                }
                if (item.getSkinConcerns() != null && !item.getSkinConcerns().isEmpty()) {
                    builder.append("  Ho tro: ").append(String.join(", ", item.getSkinConcerns())).append("\n");
                }
                // Giá
                if (item.getMinPrice() != null && item.getMaxPrice() != null) {
                    builder.append("  Gia: ").append(item.getMinPrice().toPlainString())
                           .append(" - ").append(item.getMaxPrice().toPlainString()).append(" VND\n");
                } else if (item.getMinPrice() != null) {
                    builder.append("  Gia: ").append(item.getMinPrice().toPlainString()).append(" VND\n");
                }
                // Mô tả
                if (item.getDescription() != null && !item.getDescription().isBlank()) {
                    String desc = item.getDescription().trim();
                    if (desc.length() > 120) desc = desc.substring(0, 120) + "...";
                    builder.append("  Mo ta: ").append(desc).append("\n");
                }
                // Thành phần (rút gọn)
                if (item.getIngredients() != null && !item.getIngredients().isBlank()) {
                    String ing = item.getIngredients().trim();
                    if (ing.length() > 100) ing = ing.substring(0, 100) + "...";
                    builder.append("  Thanh phan: ").append(ing).append("\n");
                }
                // Hướng dẫn sử dụng (rút gọn)
                if (item.getUsageInstructions() != null && !item.getUsageInstructions().isBlank()) {
                    String usage = item.getUsageInstructions().trim();
                    if (usage.length() > 100) usage = usage.substring(0, 100) + "...";
                    builder.append("  Cach dung: ").append(usage).append("\n");
                }
                // Trạng thái (catalog-service chỉ trả về sản phẩm đang active)
                builder.append("  Con ban: Co\n");
                builder.append("\n");
            }
        }

        builder.append("\nCau hoi cua khach: ").append(userMessage == null ? "" : userMessage.trim());
        builder.append("\n\nVi du JSON tra ve:\n");
        builder.append("- Goi y san pham: {\"in_scope\": true, \"is_safety_sensitive\": false, \"recommendation_intent\": true, \"selected_product_ids\": [\"uuid-1\", \"uuid-2\"], \"reply\": \"1. [Ten sp 1]: [1-2 cau ly do].\\n2. [Ten sp 2]: [1-2 cau ly do].\"}\n");
        builder.append("- Hoi gia/con hang/thanh phan/cach dung cua san pham cu the CO trong danh sach: {\"in_scope\": true, \"is_safety_sensitive\": false, \"recommendation_intent\": false, \"selected_product_ids\": [\"uuid-cua-san-pham-do\"], \"reply\": \"[Tra loi chinh xac tu du lieu, vi du: Gia: X VND, Con ban: Co, Cach dung: ...] [**uuid-cua-san-pham-do**]\"}\n");
        builder.append("- Hoi san pham cu the nhung KHONG CO trong danh sach (het hang/ngung ban): {\"in_scope\": true, \"is_safety_sensitive\": false, \"recommendation_intent\": true, \"selected_product_ids\": [\"uuid-sp-thay-the\"], \"reply\": \"San pham [Ten sp khach hoi] hien tai khong co san hoac da het hang tai cua hang. Tuy nhien, ban co the tham khao san pham thay the: [Ten sp thay the] co san [**uuid-sp-thay-the**]\"}\n");
        builder.append("- Cau hoi kien thuc skincare (khong hoi san pham cu the): {\"in_scope\": true, \"is_safety_sensitive\": false, \"recommendation_intent\": false, \"selected_product_ids\": [], \"reply\": \"[Tra loi kien thuc ngan gon.]\"}");
        return builder.toString();
    }

    public String buildProductSelectionPrompt(
            ContextRetrievalService.ContextSnapshot snapshot,
            String userMessage
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Ban la chuyen gia tu van my pham thong minh. Tra loi bang tieng Viet.\n");
        builder.append("QUY TAC BAT BUOC:\n");
        builder.append("- Chi duoc gioi thieu san pham trong danh sach ben duoi. KHONG duoc tu bia ra san pham nao khac.\n");
        builder.append("- Chon loc va sap xep toi da 5 san pham phu hop nhat tu danh sach ung vien.\n");
        builder.append("- Tra ve mot doi tuong JSON hop le duy nhat voi hai truong: \"selected_product_ids\" (danh sach UUID) va \"reply\" (noi dung tu van tu nhien, thuyet phuc bang tieng Viet).\n");
        builder.append("- KHONG viet bat ky ky tu nao khac ngoai JSON.\n\n");

        appendProfile(builder, snapshot.profile());

        builder.append("Danh sach san pham ung vien:\n");
        if (snapshot.items() == null || snapshot.items().isEmpty()) {
            builder.append("- Khong co san pham nao\n");
        } else {
            for (CatalogSemanticSearchItem item : snapshot.items()) {
                if (item == null || item.getProductId() == null || item.getName() == null) {
                    continue;
                }
                builder.append("- ID: ").append(item.getProductId().toString()).append("\n");
                builder.append("  Ten: ").append(item.getName().trim()).append("\n");
                if (item.getBrandName() != null) builder.append("  Thuong hieu: ").append(item.getBrandName().trim()).append("\n");
                if (item.getCategoryName() != null) builder.append("  Nhom: ").append(item.getCategoryName().trim()).append("\n");
                if (item.getSuitableSkinTypes() != null && !item.getSuitableSkinTypes().isEmpty()) {
                    builder.append("  Phu hop da: ").append(String.join(", ", item.getSuitableSkinTypes())).append("\n");
                }
                if (item.getSkinConcerns() != null && !item.getSkinConcerns().isEmpty()) {
                    builder.append("  Ho tro: ").append(String.join(", ", item.getSkinConcerns())).append("\n");
                }
                if (item.getDescription() != null && !item.getDescription().isBlank()) {
                    builder.append("  Mo ta: ").append(item.getDescription().trim()).append("\n");
                }
                builder.append("\n");
            }
        }

        builder.append("\nCau hoi cua khach: ").append(userMessage == null ? "" : userMessage.trim());
        builder.append("\n\nTra ve JSON dang:\n{\n  \"selected_product_ids\": [\"uuid\"],\n  \"reply\": \"Noi dung tu van...\"\n}");
        return builder.toString();
    }

    /**
     * Build prompt cho trường hợp có sản phẩm gợi ý.
     * Prompt ngắn gọn, buộc AI phải dùng sản phẩm từ context.
     * KHÔNG bao gồm chat history để tránh model nhỏ bị lạc đề.
     */
    public String buildProductPrompt(
            ContextRetrievalService.ContextSnapshot snapshot,
            String userMessage
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Ban la tro ly tu van my pham. Tra loi bang tieng Viet.\n");
        builder.append("QUY TAC BAT BUOC:\n");
        builder.append("- Chi duoc gioi thieu san pham trong danh sach ben duoi. KHONG duoc tu bia ra san pham nao khac.\n");
        builder.append("- Giai thich ngan gon tai sao san pham phu hop voi nhu cau cua khach.\n");
        builder.append("- Neu khong co san pham phu hop, noi ro la chua co san pham phu hop trong cua hang.\n");
        builder.append("- Tra loi ngan gon, thuc te, toi da 3-4 cau.\n\n");

        appendProfile(builder, snapshot.profile());
        appendProductList(builder, snapshot.items());

        builder.append("\nCau hoi cua khach: ").append(userMessage == null ? "" : userMessage.trim());
        builder.append("\n\nTra loi:");
        return builder.toString();
    }

    /**
     * Build prompt cho câu hỏi kiến thức (không cần gợi ý sản phẩm).
     * Prompt tối giản, chỉ có câu hỏi + hướng dẫn.
     */
    public String buildKnowledgePrompt(String userMessage) {
        return "Ban la tro ly tu van my pham va cham soc da. Tra loi bang tieng Viet.\n"
                + "QUY TAC:\n"
                + "- Chi tra loi cac cau hoi lien quan den my pham, cham soc da, thanh phan, quy trinh skincare.\n"
                + "- Tra loi ngan gon, thuc te, chinh xac. Toi da 4-5 cau.\n"
                + "- Khong duoc tu bia ra ten san pham cu the.\n\n"
                + "Cau hoi: " + (userMessage == null ? "" : userMessage.trim())
                + "\n\nTra loi:";
    }

    /**
     * Build prompt đầy đủ (backward compatible) — dùng cho trường hợp cũ.
     */
    public String buildPrompt(
            ContextRetrievalService.ContextSnapshot snapshot,
            List<ContextMessage> history,
            String userMessage
    ) {
        // Nếu có sản phẩm trong snapshot, dùng prompt sản phẩm (bỏ history)
        if (snapshot != null && snapshot.items() != null && !snapshot.items().isEmpty()) {
            return buildProductPrompt(snapshot, userMessage);
        }
        // Nếu không có sản phẩm, dùng prompt kiến thức
        return buildKnowledgePrompt(userMessage);
    }

    private void appendProfile(StringBuilder builder, CustomerProfileResponse profile) {
        if (profile == null) {
            return;
        }
        builder.append("Thong tin khach hang:\n");
        if (profile.getSkinType() != null && !profile.getSkinType().isBlank()) {
            builder.append("- Loai da: ").append(profile.getSkinType()).append("\n");
        }
        if (profile.getSkinConcerns() != null && !profile.getSkinConcerns().isEmpty()) {
            builder.append("- Van de da: ").append(String.join(", ", profile.getSkinConcerns())).append("\n");
        }
        builder.append("\n");
    }

    /**
     * Chỉ append các thông tin CỐT LÕI của sản phẩm:
     * tên, thương hiệu, category, loại da phù hợp, vấn đề da hỗ trợ.
     * KHÔNG append description, ingredients, usageInstructions dài dòng
     * để giảm kích thước prompt → model trả lời nhanh hơn và ít lạc đề hơn.
     */
    private void appendProductList(StringBuilder builder, List<CatalogSemanticSearchItem> items) {
        builder.append("Danh sach san pham co san trong cua hang:\n");
        if (items == null || items.isEmpty()) {
            builder.append("- Khong co san pham nao\n");
            return;
        }
        int index = 1;
        for (CatalogSemanticSearchItem item : items) {
            if (item == null || item.getName() == null || item.getName().isBlank()) {
                continue;
            }
            builder.append(index).append(". ").append(item.getName().trim());
            if (item.getBrandName() != null && !item.getBrandName().isBlank()) {
                builder.append(" (").append(item.getBrandName().trim()).append(")");
            }
            if (item.getCategoryName() != null && !item.getCategoryName().isBlank()) {
                builder.append(" - ").append(item.getCategoryName().trim());
            }
            List<String> details = new java.util.ArrayList<>();
            if (item.getSuitableSkinTypes() != null && !item.getSuitableSkinTypes().isEmpty()) {
                details.add("Phu hop: " + String.join(", ", item.getSuitableSkinTypes()));
            }
            if (item.getSkinConcerns() != null && !item.getSkinConcerns().isEmpty()) {
                details.add("Ho tro: " + String.join(", ", item.getSkinConcerns()));
            }
            if (!details.isEmpty()) {
                builder.append(" | ").append(String.join("; ", details));
            }
            builder.append("\n");
            index++;
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
