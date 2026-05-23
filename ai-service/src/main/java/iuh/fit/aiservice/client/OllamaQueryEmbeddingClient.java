package iuh.fit.aiservice.client;

import iuh.fit.aiservice.config.AiEmbeddingProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Component
public class OllamaQueryEmbeddingClient implements QueryEmbeddingClient {

    private final WebClient webClient;
    private final AiEmbeddingProperties embeddingProperties;

    public OllamaQueryEmbeddingClient(WebClient.Builder webClientBuilder, AiEmbeddingProperties embeddingProperties) {
        this.webClient = webClientBuilder.build();
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public List<Double> embed(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        AiEmbeddingProperties.Ollama ollama = embeddingProperties.getOllama();
        if (ollama == null || !StringUtils.hasText(ollama.getBaseUrl()) || !StringUtils.hasText(ollama.getModel())) {
            throw new IllegalStateException("Ollama embedding base-url or model is missing");
        }

        String url = normalizeBaseUrl(ollama.getBaseUrl()) + "/api/embeddings";
        OllamaEmbeddingRequest request = new OllamaEmbeddingRequest();
        request.setModel(ollama.getModel());
        request.setPrompt(text);

        Duration timeout = ollama.getTimeout();
        OllamaEmbeddingResponse response = webClient.post()
                .uri(url)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaEmbeddingResponse.class)
                .block(timeout == null ? Duration.ofSeconds(30) : timeout);

        if (response == null || response.getEmbedding() == null) {
            return List.of();
        }
        validateDimension(response.getEmbedding());
        return response.getEmbedding();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "http://localhost:11434";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void validateDimension(List<Double> embedding) {
        int expected = embeddingProperties.getVectorDimension();
        if (expected > 0 && embedding.size() != expected) {
            throw new IllegalStateException(
                    "Unexpected query embedding dimension: expected=" + expected + ", actual=" + embedding.size()
            );
        }
    }

    public static class OllamaEmbeddingRequest {
        private String model;
        private String prompt;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }

    public static class OllamaEmbeddingResponse {
        private List<Double> embedding;

        public List<Double> getEmbedding() {
            return embedding;
        }

        public void setEmbedding(List<Double> embedding) {
            this.embedding = embedding;
        }
    }
}
