package iuh.fit.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai.cache")
public class AiCacheProperties {

    private Duration queryTtl;
    private Duration contextTtl;

    public Duration getQueryTtl() {
        return queryTtl;
    }

    public void setQueryTtl(Duration queryTtl) {
        this.queryTtl = queryTtl;
    }

    public Duration getContextTtl() {
        return contextTtl;
    }

    public void setContextTtl(Duration contextTtl) {
        this.contextTtl = contextTtl;
    }
}
