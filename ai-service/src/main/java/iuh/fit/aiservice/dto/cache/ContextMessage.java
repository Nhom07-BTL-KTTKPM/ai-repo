package iuh.fit.aiservice.dto.cache;

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
public class ContextMessage {

    private MessageRole role;
    private String content;
    private Instant createdAt;
}