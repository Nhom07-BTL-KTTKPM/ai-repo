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
        String url = buildGenerateUrl(properties.getBaseUrl());
        OllamaGenerateRequest request = OllamaGenerateRequest.builder()
                .model(properties.getModel())
                .prompt(prompt)
                .stream(false)
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
        return text.trim();
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
