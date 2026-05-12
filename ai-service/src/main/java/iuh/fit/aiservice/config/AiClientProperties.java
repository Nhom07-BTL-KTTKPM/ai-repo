package iuh.fit.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.clients")
public class AiClientProperties {

    private String internalHeaderName;
    private String internalApiKey;

    public String getInternalHeaderName() {
        return internalHeaderName;
    }

    public void setInternalHeaderName(String internalHeaderName) {
        this.internalHeaderName = internalHeaderName;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }
}
