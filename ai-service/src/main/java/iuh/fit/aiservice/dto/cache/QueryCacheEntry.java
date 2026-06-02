package iuh.fit.aiservice.dto.cache;

import iuh.fit.aiservice.dto.client.CatalogSemanticSearchItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryCacheEntry {

    private List<String> productIds = new ArrayList<>();
    private List<CatalogSemanticSearchItem> items = new ArrayList<>();
    private Instant cachedAt;
}
