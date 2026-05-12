package iuh.fit.aiservice.model;

import iuh.fit.aiservice.model.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "ai_chat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatSession {

    @Id
    private String id;

    @Indexed
    private String customerId;

    @Indexed
    private SessionStatus status;

    @Indexed
    private Instant createdAt;

    @Indexed
    private Instant lastMessageAt;
}
