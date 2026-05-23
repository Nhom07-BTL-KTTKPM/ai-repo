package iuh.fit.aiservice.recommendation.repository;

import iuh.fit.aiservice.recommendation.model.RecommendationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, UUID> {

    List<RecommendationEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    List<RecommendationEntity> findByCustomerIdOrderByScoreDescCreatedAtDesc(UUID customerId, Pageable pageable);

    List<RecommendationEntity> findByCustomerIdOrderByScoreDescCreatedAtAsc(UUID customerId, Pageable pageable);

    List<RecommendationEntity> findByCustomerIdAndProductIdIn(UUID customerId, List<UUID> productIds);

    void deleteByCustomerIdAndIdNotIn(UUID customerId, List<UUID> ids);

    void deleteByCustomerId(UUID customerId);

    @Query("select distinct r.customerId from RecommendationEntity r")
    List<UUID> findDistinctCustomerIds();
}
