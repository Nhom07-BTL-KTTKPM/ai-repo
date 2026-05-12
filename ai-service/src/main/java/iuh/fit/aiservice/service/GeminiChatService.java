package iuh.fit.aiservice.service;

import iuh.fit.aiservice.client.GeminiClient;
import iuh.fit.aiservice.config.AiRetryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class GeminiChatService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiChatService.class);
    private static final String FALLBACK_MESSAGE = "Xin loi, he thong dang ban. Vui long thu lai sau.";

    private final GeminiClient geminiClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final AiRetryProperties retryProperties;

    public GeminiChatService(
            GeminiClient geminiClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            AiRetryProperties retryProperties
    ) {
        this.geminiClient = geminiClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.retryProperties = retryProperties;
    }

    public GeminiClient.GeminiChatResult generateReply(String prompt) {
        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("gemini");
        return circuitBreaker.run(
                () -> retryGenerate(prompt),
                throwable -> fallbackResult(throwable)
        );
    }

    private GeminiClient.GeminiChatResult retryGenerate(String prompt) {
        int maxAttempts = Math.max(1, retryProperties.getMaxAttempts());
        Duration backoff = retryProperties.getBackoff().getInitial();
        Duration maxBackoff = retryProperties.getBackoff().getMax();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return geminiClient.generateContent(prompt);
            } catch (Exception ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                sleep(backoff);
                backoff = nextBackoff(backoff, maxBackoff);
            }
        }
        return fallbackResult(null);
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

    private GeminiClient.GeminiChatResult fallbackResult(Throwable throwable) {
        logger.warn("Gemini fallback triggered", throwable);
        return new GeminiClient.GeminiChatResult(FALLBACK_MESSAGE, 0, 0, 0, 0);
    }
}
