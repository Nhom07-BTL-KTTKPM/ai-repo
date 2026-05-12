package iuh.fit.aiservice.repo;

import iuh.fit.aiservice.model.AiRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AiRecommendationRepository extends MongoRepository<AiRecommendation, String> {
    List<AiRecommendation> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);
}
