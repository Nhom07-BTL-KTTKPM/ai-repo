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

        // Chỉ lưu log và trigger recommend nếu xem đủ >= 10 giây
        boolean qualifiedView = request.getDurationSeconds() != null
                && request.getDurationSeconds() >= 10;

        return trackBehaviorInternal(eventRequest, customerId, qualifiedView);
    }

    @Transactional
    public ViewLogResponse trackBehavior(BehaviorEventRequest request, UUID customerId) {
        // Gửi cờ trigger=true cho Cart/Purchase, nhưng bên trong vẫn bị chặn lại nếu sản phẩm đã tồn tại log
        return trackBehaviorInternal(request, customerId, true);
    }

    private ViewLogResponse trackBehaviorInternal(BehaviorEventRequest request, UUID customerId, boolean triggerRecommendation) {
        ProductViewSource source = request.getSource() == null
            ? ProductViewSource.DIRECT
            : request.getSource();

        // Kiểm tra xem user đã xem/tương tác với sản phẩm này bao giờ chưa
        boolean alreadyViewed = false;
        if (customerId != null) {
            alreadyViewed = viewLogRepository.existsByCustomerIdAndProductId(customerId, request.getProductId());
        }

        ProductViewLogEntity entity = ProductViewLogEntity.builder()
            .customerId(customerId)
            .productId(request.getProductId())
            .source(source)
            .eventType(request.getEventType())
            .viewedAt(Instant.now())
            .build();

        ProductViewLogEntity saved = viewLogRepository.save(entity);

        // NẾU đã xem rồi thì KHÔNG chạy recommend nữa để tiết kiệm tài nguyên
        if (customerId != null && triggerRecommendation && !alreadyViewed) {
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
