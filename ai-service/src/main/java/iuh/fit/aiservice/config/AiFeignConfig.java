package iuh.fit.aiservice.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class AiFeignConfig {

    @Bean
    public RequestInterceptor internalApiKeyInterceptor(AiClientProperties clientProperties) {
        return template -> {
            String headerName = clientProperties.getInternalHeaderName();
            String apiKey = clientProperties.getInternalApiKey();
            if (headerName != null && !headerName.isBlank() && apiKey != null && !apiKey.isBlank()) {
                template.header(headerName, apiKey);
            }
        };
    }
}