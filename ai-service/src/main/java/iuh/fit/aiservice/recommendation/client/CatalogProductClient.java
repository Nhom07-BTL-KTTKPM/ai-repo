package iuh.fit.aiservice.recommendation.client;

import iuh.fit.aiservice.config.AiFeignConfig;
import iuh.fit.aiservice.recommendation.client.dto.CatalogPageResponse;
import iuh.fit.aiservice.recommendation.client.dto.CatalogProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "catalog-service", contextId = "catalog-product-client", configuration = AiFeignConfig.class)
public interface CatalogProductClient {

    @GetMapping("/api/v1/catalog/products/{id}")
    CatalogProductResponse getProductById(@PathVariable("id") UUID id);

    @GetMapping("/api/v1/catalog/products/category/{categoryId}")
    CatalogPageResponse<CatalogProductResponse> getProductsByCategory(
            @PathVariable("categoryId") UUID categoryId,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );

    @GetMapping("/api/v1/catalog/products/brand/{brandId}")
    CatalogPageResponse<CatalogProductResponse> getProductsByBrand(
            @PathVariable("brandId") UUID brandId,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );
}
