package iuh.fit.aiservice.recommendation.repository;

import iuh.fit.aiservice.recommendation.model.ProductViewLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductViewLogRepository extends JpaRepository<ProductViewLogEntity, UUID> {

    List<ProductViewLogEntity> findByCustomerIdOrderByViewedAtDesc(UUID customerId, Pageable pageable);

    void deleteByViewedAtBefore(Instant cutoff);
}
