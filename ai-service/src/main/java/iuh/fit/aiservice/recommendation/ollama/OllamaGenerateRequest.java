package iuh.fit.aiservice.recommendation.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaGenerateRequest {

    private String model;
    private String prompt;
    private boolean stream;
}
