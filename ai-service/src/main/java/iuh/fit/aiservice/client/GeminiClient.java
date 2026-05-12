package iuh.fit.aiservice.client;

import iuh.fit.aiservice.config.AiGeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class GeminiClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiClient.class);

    private final WebClient webClient;
    private final AiGeminiProperties geminiProperties;

    public GeminiClient(WebClient.Builder webClientBuilder, AiGeminiProperties geminiProperties) {
        this.webClient = webClientBuilder.build();
        this.geminiProperties = geminiProperties;
    }

    public GeminiChatResult generateContent(String prompt) {
        String url = buildGenerateUrl();
        GeminiGenerateContentRequest request = GeminiGenerateContentRequest.fromPrompt(prompt);
        Instant start = Instant.now();
        GeminiGenerateContentResponse response = webClient.post()
                .uri(url)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiGenerateContentResponse.class)
                .block();

        long latencyMs = Duration.between(start, Instant.now()).toMillis();
        GeminiGenerateContentResponse safeResponse = response == null ? new GeminiGenerateContentResponse() : response;
        UsageMetadata usage = safeResponse.getUsageMetadata();
        GeminiGenerateContentResponse.Candidate candidate = safeResponse.firstCandidate();
        String text = candidate == null ? null : candidate.extractText();

        GeminiChatResult result = new GeminiChatResult(
                text == null ? "" : text,
                usage == null ? 0 : usage.getPromptTokenCount(),
                usage == null ? 0 : usage.getCandidatesTokenCount(),
                usage == null ? 0 : usage.getTotalTokenCount(),
                latencyMs
        );

        logger.info(
                "Gemini usage promptTokens={}, candidateTokens={}, totalTokens={}, latencyMs={}",
                result.promptTokenCount(),
                result.candidateTokenCount(),
                result.totalTokenCount(),
                result.latencyMs()
        );

        return result;
    }

    public GeminiEmbeddingResult embedContent(String text) {
        String url = buildEmbeddingUrl();
        GeminiEmbeddingRequest request = GeminiEmbeddingRequest.fromText(text);
        Instant start = Instant.now();
        GeminiEmbeddingResponse response = webClient.post()
                .uri(url)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiEmbeddingResponse.class)
                .block();

        long latencyMs = Duration.between(start, Instant.now()).toMillis();
        List<Double> values = response == null || response.getEmbedding() == null
                ? List.of()
                : response.getEmbedding().getValues();
        return new GeminiEmbeddingResult(values, latencyMs);
    }

    private String buildGenerateUrl() {
        return String.format(
                "%s/%s:generateContent?key=%s",
                geminiProperties.getEndpoint(),
                geminiProperties.getModel(),
                geminiProperties.getApiKey()
        );
    }

    private String buildEmbeddingUrl() {
        return String.format(
                "%s/%s:embedContent?key=%s",
                geminiProperties.getEndpoint(),
                geminiProperties.getEmbeddingModel(),
                geminiProperties.getApiKey()
        );
    }

    public record GeminiChatResult(
            String text,
            int promptTokenCount,
            int candidateTokenCount,
            int totalTokenCount,
            long latencyMs
    ) {
    }

    public record GeminiEmbeddingResult(List<Double> values, long latencyMs) {
    }

    public static class GeminiGenerateContentRequest {
        private List<Content> contents = new ArrayList<>();

        public List<Content> getContents() {
            return contents;
        }

        public void setContents(List<Content> contents) {
            this.contents = contents;
        }

        public static GeminiGenerateContentRequest fromPrompt(String prompt) {
            Part part = new Part();
            part.setText(prompt);
            Content content = new Content();
            content.setRole("user");
            content.setParts(List.of(part));
            GeminiGenerateContentRequest request = new GeminiGenerateContentRequest();
            request.setContents(List.of(content));
            return request;
        }

        public static class Content {
            private String role;
            private List<Part> parts = new ArrayList<>();

            public String getRole() {
                return role;
            }

            public void setRole(String role) {
                this.role = role;
            }

            public List<Part> getParts() {
                return parts;
            }

            public void setParts(List<Part> parts) {
                this.parts = parts;
            }
        }

        public static class Part {
            private String text;

            public String getText() {
                return text;
            }

            public void setText(String text) {
                this.text = text;
            }
        }
    }

    public static class GeminiGenerateContentResponse {
        private List<Candidate> candidates = new ArrayList<>();
        private UsageMetadata usageMetadata;

        public List<Candidate> getCandidates() {
            return candidates;
        }

        public void setCandidates(List<Candidate> candidates) {
            this.candidates = candidates;
        }

        public UsageMetadata getUsageMetadata() {
            return usageMetadata;
        }

        public void setUsageMetadata(UsageMetadata usageMetadata) {
            this.usageMetadata = usageMetadata;
        }

        public Candidate firstCandidate() {
            return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
        }

        public static class Candidate {
            private Content content;

            public Content getContent() {
                return content;
            }

            public void setContent(Content content) {
                this.content = content;
            }

            public String extractText() {
                if (content == null || content.getParts() == null) {
                    return null;
                }
                return content.getParts().stream()
                        .map(Part::getText)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
        }

        public static class Content {
            private List<Part> parts = new ArrayList<>();

            public List<Part> getParts() {
                return parts;
            }

            public void setParts(List<Part> parts) {
                this.parts = parts;
            }
        }

        public static class Part {
            private String text;

            public String getText() {
                return text;
            }

            public void setText(String text) {
                this.text = text;
            }
        }
    }

    public static class GeminiEmbeddingRequest {
        private Content content;

        public Content getContent() {
            return content;
        }

        public void setContent(Content content) {
            this.content = content;
        }

        public static GeminiEmbeddingRequest fromText(String text) {
            Part part = new Part();
            part.setText(text);
            Content content = new Content();
            content.setParts(List.of(part));
            GeminiEmbeddingRequest request = new GeminiEmbeddingRequest();
            request.setContent(content);
            return request;
        }

        public static class Content {
            private List<Part> parts = new ArrayList<>();

            public List<Part> getParts() {
                return parts;
            }

            public void setParts(List<Part> parts) {
                this.parts = parts;
            }
        }

        public static class Part {
            private String text;

            public String getText() {
                return text;
            }

            public void setText(String text) {
                this.text = text;
            }
        }
    }

    public static class GeminiEmbeddingResponse {
        private Embedding embedding;

        public Embedding getEmbedding() {
            return embedding;
        }

        public void setEmbedding(Embedding embedding) {
            this.embedding = embedding;
        }

        public static class Embedding {
            private List<Double> values = new ArrayList<>();

            public List<Double> getValues() {
                return values;
            }

            public void setValues(List<Double> values) {
                this.values = values;
            }
        }
    }

    public static class UsageMetadata {
        private int promptTokenCount;
        private int candidatesTokenCount;
        private int totalTokenCount;

        public int getPromptTokenCount() {
            return promptTokenCount;
        }

        public void setPromptTokenCount(int promptTokenCount) {
            this.promptTokenCount = promptTokenCount;
        }

        public int getCandidatesTokenCount() {
            return candidatesTokenCount;
        }

        public void setCandidatesTokenCount(int candidatesTokenCount) {
            this.candidatesTokenCount = candidatesTokenCount;
        }

        public int getTotalTokenCount() {
            return totalTokenCount;
        }

        public void setTotalTokenCount(int totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
        }
    }
}