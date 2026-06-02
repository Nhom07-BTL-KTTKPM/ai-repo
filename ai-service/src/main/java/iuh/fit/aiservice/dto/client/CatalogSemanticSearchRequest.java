package iuh.fit.aiservice.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSemanticSearchRequest {

    private List<Double> embedding;
    private Integer topK;
    private Double minScore;
    private String queryText;
}
