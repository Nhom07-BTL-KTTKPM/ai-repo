package iuh.fit.aiservice;

import iuh.fit.aiservice.config.AiCacheProperties;
import iuh.fit.aiservice.config.AiChatRetentionProperties;
import iuh.fit.aiservice.config.AiClientProperties;
import iuh.fit.aiservice.config.AiGeminiProperties;
import iuh.fit.aiservice.config.AiRagProperties;
import iuh.fit.aiservice.config.AiRetryProperties;
import iuh.fit.aiservice.recommendation.config.OllamaProperties;
import iuh.fit.aiservice.recommendation.config.RecommendationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableJpaRepositories(basePackages = "iuh.fit.aiservice.recommendation.repository")
@EnableMongoRepositories(basePackages = "iuh.fit.aiservice.repo")
@EnableScheduling
@EnableConfigurationProperties({
        AiGeminiProperties.class,
        AiRagProperties.class,
        AiRetryProperties.class,
        AiCacheProperties.class,
        AiClientProperties.class,
        AiChatRetentionProperties.class,
        RecommendationProperties.class,
        OllamaProperties.class
})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }

}
