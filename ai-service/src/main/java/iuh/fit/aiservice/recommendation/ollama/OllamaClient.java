package iuh.fit.aiservice.recommendation.ollama;

import iuh.fit.aiservice.recommendation.config.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class OllamaClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);

    private final WebClient webClient;
    private final OllamaProperties properties;

    public OllamaClient(WebClient.Builder webClientBuilder, OllamaProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    public String generate(String prompt) {
        return generate(prompt, null);
    }

    public String generate(String prompt, String format) {
        String url = buildGenerateUrl(properties.getBaseUrl());
        String modelName = properties.getModel();
        
        OllamaGenerateRequest request = OllamaGenerateRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .stream(false)
                .format(format)
                .think(false)
                .options(java.util.Map.of(
                        "num_predict", 1024,
                        "num_ctx", 4096
                ))
                .build();

        Duration timeout = properties.getTimeout();
        OllamaGenerateResponse response = webClient.post()
            .uri(url)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OllamaGenerateResponse.class)
            .block(timeout == null ? Duration.ofSeconds(30) : timeout);

        String text = response == null ? null : response.getResponse();
        if (text == null || text.isBlank()) {
            return "";
        }
        // Strip thinking block if model returned one despite think=false
        text = stripThinkingBlock(text);
        return text.trim();
    }

    private String stripThinkingBlock(String text) {
        if (text == null) return "";
        // qwen3 thinking models wrap reasoning in <think>...</think> tags
        int thinkEnd = text.lastIndexOf("</think>");
        if (thinkEnd != -1) {
            String afterThink = text.substring(thinkEnd + 8).trim();
            if (!afterThink.isBlank()) {
                return afterThink;
            }
        }
        return text;
    }

    private String buildGenerateUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/api/generate")) {
            return normalized;
        }
        if (normalized.endsWith("/api")) {
            return normalized + "/generate";
        }
        return normalized + "/api/generate";
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:11434";
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/api")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        if (trimmed.endsWith("/v1")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed;
    }
}
