package iuh.fit.aiservice.model;

import iuh.fit.aiservice.model.enums.RecommendationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "ai_recommendations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendation {

    @Id
    private String id;

    @Indexed
    private String customerId;

    @Indexed
    private String productId;

    private Double score;

    private String reason;

    private RecommendationType type;

    private Boolean isClicked;

    @Indexed
    private Instant createdAt;
}
