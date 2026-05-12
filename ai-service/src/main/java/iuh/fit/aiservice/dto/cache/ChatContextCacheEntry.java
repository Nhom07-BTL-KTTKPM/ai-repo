package iuh.fit.aiservice.dto.cache;

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
public class ChatContextCacheEntry {

    private String sessionId;
    private String customerId;
    private Instant cachedAt;
    private List<ContextMessage> messages = new ArrayList<>();
}
