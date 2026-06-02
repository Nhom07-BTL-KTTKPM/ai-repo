package iuh.fit.aiservice.recommendation.service;

import iuh.fit.aiservice.recommendation.config.RecommendationProperties;
import iuh.fit.aiservice.recommendation.model.RecommendationEntity;
import iuh.fit.aiservice.recommendation.repository.ProductViewLogRepository;
import iuh.fit.aiservice.recommendation.repository.RecommendationRepository;
import iuh.fit.aiservice.repo.ProductViewLogMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RecommendationCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationCleanupScheduler.class);

    private final RecommendationRepository recommendationRepository;
    private final ProductViewLogRepository viewLogRepository;
    private final ProductViewLogMongoRepository mongoViewLogRepository;
    private final RecommendationProperties properties;

    public RecommendationCleanupScheduler(
            RecommendationRepository recommendationRepository,
            ProductViewLogRepository viewLogRepository,
            ProductViewLogMongoRepository mongoViewLogRepository,
            RecommendationProperties properties
    ) {
        this.recommendationRepository = recommendationRepository;
        this.viewLogRepository = viewLogRepository;
        this.mongoViewLogRepository = mongoViewLogRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${ai.recommendation.cleanup-cron:0 0 2 * * *}")
    public void cleanupDaily() {
        int keepTop = Math.max(properties.getCleanupKeepTop(), 1);
        int retentionDays = Math.max(properties.getCleanupRetentionDays(), 1);
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        viewLogRepository.deleteByViewedAtBefore(cutoff);
        mongoViewLogRepository.deleteByViewedAtBefore(cutoff);

        List<UUID> customerIds = recommendationRepository.findDistinctCustomerIds();
        for (UUID customerId : customerIds) {
            List<RecommendationEntity> keep = recommendationRepository.findByCustomerIdOrderByScoreDescCreatedAtAsc(
                    customerId,
                    PageRequest.of(0, keepTop)
            );
            if (keep.isEmpty()) {
                continue;
            }
            List<UUID> keepIds = keep.stream()
                    .map(RecommendationEntity::getId)
                    .toList();
            recommendationRepository.deleteByCustomerIdAndIdNotIn(customerId, keepIds);
        }

        logger.info("Recommendation cleanup completed with keepTop={} retentionDays={}", keepTop, retentionDays);
    }
}
