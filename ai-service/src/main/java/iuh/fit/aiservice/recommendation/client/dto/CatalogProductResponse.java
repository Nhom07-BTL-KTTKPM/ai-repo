package iuh.fit.aiservice.recommendation.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProductResponse {

    private UUID id;
    private String name;
    private UUID categoryId;
    private String categoryName;
    private UUID brandId;
    private String brandName;
    private List<String> suitableSkinTypes;
    private List<String> skinConcerns;
    private String ingredients;
    private Boolean isActive;
}
