package iuh.fit.aiservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    @NotBlank
    private String customerId;

    private String sessionId;

    @NotBlank
    private String message;

    @Min(1)
    @Max(50)
    private Integer topK;
}
