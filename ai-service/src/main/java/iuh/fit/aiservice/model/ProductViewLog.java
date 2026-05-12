package iuh.fit.aiservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "product_view_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductViewLog {

    @Id
    private String id;

    @Indexed
    private String customerId;

    @Indexed
    private String productId;

    @Indexed
    private Instant viewedAt;

    private String source;
}
