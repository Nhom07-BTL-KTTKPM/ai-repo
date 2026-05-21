package iuh.fit.aiservice.recommendation.service;

import iuh.fit.aiservice.recommendation.dto.request.BehaviorEventRequest;
import iuh.fit.aiservice.recommendation.dto.request.ViewLogRequest;
import iuh.fit.aiservice.recommendation.dto.response.ViewLogResponse;
import iuh.fit.aiservice.recommendation.model.BehaviorEventType;
import iuh.fit.aiservice.recommendation.model.ProductViewLogEntity;
import iuh.fit.aiservice.recommendation.model.ProductViewSource;
import iuh.fit.aiservice.recommendation.repository.ProductViewLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Service
public class BehaviorTrackingService {

    private final ProductViewLogRepository viewLogRepository;
    private final RecommendationGenerator recommendationGenerator;

    public BehaviorTrackingService(
            ProductViewLogRepository viewLogRepository,
            RecommendationGenerator recommendationGenerator
    ) {
        this.viewLogRepository = viewLogRepository;
        this.recommendationGenerator = recommendationGenerator;
    }

    @Transactional
    public ViewLogResponse trackView(ViewLogRequest request, UUID customerId) {
        BehaviorEventRequest eventRequest = BehaviorEventRequest.builder()
            .productId(request.getProductId())
            .eventType(BehaviorEventType.VIEW)
            .source(request.getSource())
            .customerId(request.getCustomerId())
            .build();

        return trackBehavior(eventRequest, customerId);
        }

        @Transactional
        public ViewLogResponse trackBehavior(BehaviorEventRequest request, UUID customerId) {
        ProductViewSource source = request.getSource() == null
            ? ProductViewSource.DIRECT
            : request.getSource();

        ProductViewLogEntity entity = ProductViewLogEntity.builder()
            .customerId(customerId)
            .productId(request.getProductId())
            .source(source)
            .eventType(request.getEventType())
            .viewedAt(Instant.now())
            .build();

        ProductViewLogEntity saved = viewLogRepository.save(entity);

        if (customerId != null) {
            scheduleRecommendationGeneration(customerId);
        }

        return ViewLogResponse.builder()
            .id(saved.getId())
            .viewedAt(saved.getViewedAt())
            .build();
    }

    private void scheduleRecommendationGeneration(UUID customerId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recommendationGenerator.generateAsync(customerId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recommendationGenerator.generateAsync(customerId);
            }
        });
    }
}
