package iuh.fit.aiservice.repo;

import iuh.fit.aiservice.model.AiChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AiChatSessionRepository extends MongoRepository<AiChatSession, String> {
    List<AiChatSession> findByCustomerIdOrderByLastMessageAtDesc(String customerId, Pageable pageable);
}
