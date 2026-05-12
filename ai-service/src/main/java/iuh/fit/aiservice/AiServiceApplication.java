package iuh.fit.aiservice;

import iuh.fit.aiservice.config.AiCacheProperties;
import iuh.fit.aiservice.config.AiChatRetentionProperties;
import iuh.fit.aiservice.config.AiClientProperties;
import iuh.fit.aiservice.config.AiGeminiProperties;
import iuh.fit.aiservice.config.AiRagProperties;
import iuh.fit.aiservice.config.AiRetryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties({
        AiGeminiProperties.class,
        AiRagProperties.class,
        AiRetryProperties.class,
        AiCacheProperties.class,
        AiClientProperties.class,
        AiChatRetentionProperties.class
})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }

}
