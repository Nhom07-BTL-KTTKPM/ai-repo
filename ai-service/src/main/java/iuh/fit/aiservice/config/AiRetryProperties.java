package iuh.fit.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai.retry")
public class AiRetryProperties {

    private int maxAttempts;
    private Backoff backoff = new Backoff();

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Backoff getBackoff() {
        return backoff;
    }

    public void setBackoff(Backoff backoff) {
        this.backoff = backoff;
    }

    public static class Backoff {
        private Duration initial;
        private Duration max;

        public Duration getInitial() {
            return initial;
        }

        public void setInitial(Duration initial) {
            this.initial = initial;
        }

        public Duration getMax() {
            return max;
        }

        public void setMax(Duration max) {
            this.max = max;
        }
    }
}
