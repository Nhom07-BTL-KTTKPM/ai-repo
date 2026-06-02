package iuh.fit.aiservice.dto.response;

import iuh.fit.aiservice.model.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessageItem {

    private String id;
    private String sessionId;
    private MessageRole role;
    private String content;
    private Integer tokenUsed;
    private Instant createdAt;
}
