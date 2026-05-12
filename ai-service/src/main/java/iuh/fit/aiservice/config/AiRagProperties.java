package iuh.fit.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.rag")
public class AiRagProperties {

    private int topK;
    private int maxContextTokens;
    private int maxViewLogs;
    private int maxMessages;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    public int getMaxViewLogs() {
        return maxViewLogs;
    }

    public void setMaxViewLogs(int maxViewLogs) {
        this.maxViewLogs = maxViewLogs;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }
}
