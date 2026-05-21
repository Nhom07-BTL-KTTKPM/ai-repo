package iuh.fit.aiservice.recommendation.dto.request;

import iuh.fit.aiservice.recommendation.model.ProductViewSource;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewLogRequest {

    @NotNull
    private UUID productId;

    private ProductViewSource source;

    private String customerId;
}
