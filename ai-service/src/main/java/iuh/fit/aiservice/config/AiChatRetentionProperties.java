package iuh.fit.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai.chat.retention")
public class AiChatRetentionProperties {

    private boolean enabled;
    private Duration messageTtl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getMessageTtl() {
        return messageTtl;
    }

    public void setMessageTtl(Duration messageTtl) {
        this.messageTtl = messageTtl;
    }
}
