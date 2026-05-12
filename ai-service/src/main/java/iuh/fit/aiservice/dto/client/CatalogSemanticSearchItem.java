package iuh.fit.aiservice.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSemanticSearchItem {

    private UUID productId;
    private String name;
    private String ingredients;
    private String usageInstructions;
    private List<String> suitableSkinTypes;
    private List<String> skinConcerns;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Double score;
}
