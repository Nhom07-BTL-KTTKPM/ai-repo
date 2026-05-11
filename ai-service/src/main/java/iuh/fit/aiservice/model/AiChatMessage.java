package iuh.fit.aiservice.model;

import iuh.fit.aiservice.model.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "ai_chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage {

    @Id
    private String id;

    @Indexed
    private String sessionId;

    @Indexed
    private String customerId;

    private MessageRole role;

    private String content;

    private Integer tokenUsed;

    @Indexed
    private Instant createdAt;
}
