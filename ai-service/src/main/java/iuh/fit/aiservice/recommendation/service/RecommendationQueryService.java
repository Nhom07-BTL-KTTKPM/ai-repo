package iuh.fit.aiservice.recommendation.service;

import iuh.fit.aiservice.recommendation.config.RecommendationProperties;
import iuh.fit.aiservice.recommendation.dto.response.RecommendationItemResponse;
import iuh.fit.aiservice.recommendation.model.RecommendationEntity;
import iuh.fit.aiservice.recommendation.repository.RecommendationRepository;
import iuh.fit.shared.error.BusinessException;
import iuh.fit.shared.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecommendationQueryService {

    private static final int MAX_LIMIT = 50;

    private final RecommendationRepository recommendationRepository;
    private final RecommendationProperties properties;

    public RecommendationQueryService(
            RecommendationRepository recommendationRepository,
            RecommendationProperties properties
    ) {
        this.recommendationRepository = recommendationRepository;
        this.properties = properties;
    }

    public List<RecommendationItemResponse> listRecommendations(UUID customerId, Integer limit) {
        int pageSize = resolveLimit(limit);
        List<RecommendationEntity> entities = recommendationRepository.findByCustomerIdOrderByScoreDescCreatedAtDesc(
                customerId,
                PageRequest.of(0, pageSize)
        );
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private RecommendationItemResponse toResponse(RecommendationEntity entity) {
        return RecommendationItemResponse.builder()
                .id(entity.getId())
                .productId(entity.getProductId())
                .score(entity.getScore())
                .reason(entity.getReason())
                .type(entity.getType())
                .isClicked(entity.getIsClicked())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private int resolveLimit(Integer limit) {
        int effective = limit == null ? properties.getTopK() : limit;
        if (effective < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Limit must be >= 1");
        }
        return Math.min(effective, MAX_LIMIT);
    }
}
