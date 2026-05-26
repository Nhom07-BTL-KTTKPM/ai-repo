package iuh.fit.aiservice.recommendation.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaGenerateRequest {

    private String model;
    private String prompt;
    private boolean stream;
    private String format;
    private Map<String, Object> options;
    private Boolean think;
}
