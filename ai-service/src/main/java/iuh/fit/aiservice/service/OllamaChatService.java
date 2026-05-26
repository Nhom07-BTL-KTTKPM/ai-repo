package iuh.fit.aiservice.service;

import iuh.fit.aiservice.config.AiRetryProperties;
import iuh.fit.aiservice.recommendation.ollama.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OllamaChatService {

    private static final Logger logger = LoggerFactory.getLogger(OllamaChatService.class);
    private static final String FALLBACK_MESSAGE = "Xin loi, he thong dang ban. Vui long thu lai sau.";

    private final OllamaClient ollamaClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final AiRetryProperties retryProperties;

    public OllamaChatService(
            OllamaClient ollamaClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            AiRetryProperties retryProperties
    ) {
        this.ollamaClient = ollamaClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.retryProperties = retryProperties;
    }

    public ChatResult generateReply(String prompt) {
        return generateReply(prompt, null);
    }

    public ChatResult generateReply(String prompt, String format) {
        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("ollama");
        return circuitBreaker.run(
                () -> retryGenerate(prompt, format),
                this::fallbackResult
        );
    }

    private ChatResult retryGenerate(String prompt, String format) {
        int maxAttempts = Math.max(1, retryProperties.getMaxAttempts());
        Duration backoff = retryProperties.getBackoff().getInitial();
        Duration maxBackoff = retryProperties.getBackoff().getMax();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String response = ollamaClient.generate(prompt, format);
                return new ChatResult(response == null ? "" : response, 0, false);
            } catch (Exception ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                sleep(backoff);
                backoff = nextBackoff(backoff, maxBackoff);
            }
        }
        return new ChatResult("", 0, true);
    }

    private void sleep(Duration backoff) {
        if (backoff == null || backoff.isZero() || backoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Duration nextBackoff(Duration current, Duration max) {
        if (current == null) {
            return max;
        }
        Duration next = current.multipliedBy(2);
        if (max != null && next.compareTo(max) > 0) {
            return max;
        }
        return next;
    }

    private ChatResult fallbackResult(Throwable throwable) {
        logger.warn("Ollama fallback triggered", throwable);
        return new ChatResult(FALLBACK_MESSAGE, 0, true);
    }

    public record ChatResult(String text, int tokenUsed, boolean fallback) {
    }
}
