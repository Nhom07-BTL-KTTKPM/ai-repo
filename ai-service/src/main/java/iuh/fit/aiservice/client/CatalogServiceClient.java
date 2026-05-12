package iuh.fit.aiservice.client;

import iuh.fit.aiservice.config.AiFeignConfig;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchRequest;
import iuh.fit.aiservice.dto.client.CatalogSemanticSearchResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "catalog-service", configuration = AiFeignConfig.class)
public interface CatalogServiceClient {

    @PostMapping("/internal/catalog/semantic-search")
    CatalogSemanticSearchResponse semanticSearch(@RequestBody CatalogSemanticSearchRequest request);
}
