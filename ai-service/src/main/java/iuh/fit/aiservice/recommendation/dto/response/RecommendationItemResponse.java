package iuh.fit.aiservice.recommendation.dto.response;

import iuh.fit.aiservice.model.enums.RecommendationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationItemResponse {

    private UUID id;
    private UUID productId;
    private Double score;
    private String reason;
    private RecommendationType type;
    private Boolean isClicked;
    private Instant createdAt;
}
