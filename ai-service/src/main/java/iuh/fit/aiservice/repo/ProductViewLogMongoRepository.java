package iuh.fit.aiservice.repo;

import iuh.fit.aiservice.model.ProductViewLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface ProductViewLogMongoRepository extends MongoRepository<ProductViewLog, String> {
    List<ProductViewLog> findByCustomerIdOrderByViewedAtDesc(String customerId, Pageable pageable);

    void deleteByViewedAtBefore(Instant cutoff);
}
