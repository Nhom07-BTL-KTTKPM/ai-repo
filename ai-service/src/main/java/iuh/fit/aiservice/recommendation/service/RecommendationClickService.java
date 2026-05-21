package iuh.fit.aiservice.recommendation.service;

import iuh.fit.aiservice.recommendation.dto.request.BehaviorEventRequest;
import iuh.fit.aiservice.recommendation.model.BehaviorEventType;
import iuh.fit.aiservice.recommendation.model.ProductViewSource;
import iuh.fit.aiservice.recommendation.model.RecommendationEntity;
import iuh.fit.aiservice.recommendation.repository.RecommendationRepository;
import iuh.fit.shared.error.BusinessException;
import iuh.fit.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RecommendationClickService {

    private final RecommendationRepository recommendationRepository;
    private final BehaviorTrackingService trackingService;

    public RecommendationClickService(
            RecommendationRepository recommendationRepository,
            BehaviorTrackingService trackingService
    ) {
        this.recommendationRepository = recommendationRepository;
        this.trackingService = trackingService;
    }

    @Transactional
    public void markClicked(UUID recommendationId, UUID customerId) {
        RecommendationEntity entity = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Recommendation not found"));

        if (customerId != null && !customerId.equals(entity.getCustomerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Recommendation does not belong to customer");
        }

        if (Boolean.TRUE.equals(entity.getIsClicked())) {
            return;
        }

        entity.setIsClicked(true);
        recommendationRepository.save(entity);

        BehaviorEventRequest eventRequest = BehaviorEventRequest.builder()
                .productId(entity.getProductId())
                .eventType(BehaviorEventType.RECOMMENDATION_CLICK)
                .source(ProductViewSource.RECOMMENDATION)
                .build();
        trackingService.trackBehavior(eventRequest, customerId);
    }
}
