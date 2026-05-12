package iuh.fit.aiservice.dto.response;

import iuh.fit.aiservice.model.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatSessionSummary {

    private String id;
    private SessionStatus status;
    private Instant createdAt;
    private Instant lastMessageAt;
}
