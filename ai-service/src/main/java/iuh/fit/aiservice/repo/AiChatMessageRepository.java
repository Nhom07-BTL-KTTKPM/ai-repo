package iuh.fit.aiservice.repo;

import iuh.fit.aiservice.model.AiChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AiChatMessageRepository extends MongoRepository<AiChatMessage, String> {
    List<AiChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId, Pageable pageable);
    List<AiChatMessage> findBySessionIdAndCustomerIdOrderByCreatedAtAsc(
            String sessionId,
            String customerId,
            Pageable pageable
    );
}
