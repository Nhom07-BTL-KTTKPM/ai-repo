package iuh.fit.aiservice.recommendation.controller;

import iuh.fit.aiservice.recommendation.dto.request.BehaviorEventRequest;
import iuh.fit.aiservice.recommendation.dto.request.ViewLogRequest;
import iuh.fit.aiservice.recommendation.dto.response.RecommendationItemResponse;
import iuh.fit.aiservice.recommendation.dto.response.ViewLogResponse;
import iuh.fit.aiservice.recommendation.service.BehaviorTrackingService;
import iuh.fit.aiservice.recommendation.service.RecommendationClickService;
import iuh.fit.aiservice.recommendation.service.RecommendationQueryService;
import iuh.fit.aiservice.recommendation.util.RecommendationAuthUtil;
import iuh.fit.aiservice.recommendation.util.TraceIdResolver;
import iuh.fit.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@Validated
public class RecommendationController {

    private final BehaviorTrackingService trackingService;
    private final RecommendationQueryService queryService;
    private final RecommendationClickService clickService;

    public RecommendationController(
            BehaviorTrackingService trackingService,
            RecommendationQueryService queryService,
            RecommendationClickService clickService
    ) {
        this.trackingService = trackingService;
        this.queryService = queryService;
        this.clickService = clickService;
    }

    @PostMapping("/view-log")
        @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<ViewLogResponse>> trackView(
            @Valid @RequestBody ViewLogRequest request,
            HttpServletRequest servletRequest
    ) {
        UUID customerId = RecommendationAuthUtil.resolveOptionalCustomerId(request.getCustomerId());
        ViewLogResponse response = trackingService.trackView(request, customerId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response, "View log saved", TraceIdResolver.resolve(servletRequest)));
    }

        @PostMapping("/behavior")
        @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
        public ResponseEntity<ApiResponse<ViewLogResponse>> trackBehavior(
            @Valid @RequestBody BehaviorEventRequest request,
            HttpServletRequest servletRequest
        ) {
        UUID customerId = RecommendationAuthUtil.resolveOptionalCustomerId(request.getCustomerId());
        ViewLogResponse response = trackingService.trackBehavior(request, customerId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(response, "Behavior event saved", TraceIdResolver.resolve(servletRequest)));
        }

    @GetMapping("/recommendations")
        @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<List<RecommendationItemResponse>>> listRecommendations(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest servletRequest
    ) {
        UUID resolvedCustomerId = RecommendationAuthUtil.resolveRequiredCustomerId(customerId);
        List<RecommendationItemResponse> response = queryService.listRecommendations(resolvedCustomerId, limit);
        return ResponseEntity.ok(ApiResponse.success(response, "Recommendations fetched", TraceIdResolver.resolve(servletRequest)));
    }

    @PostMapping("/recommendations/{id}/click")
        @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> markClicked(
            @PathVariable("id") UUID recommendationId,
            @RequestParam(required = false) String customerId,
            HttpServletRequest servletRequest
    ) {
        UUID resolvedCustomerId = RecommendationAuthUtil.resolveRequiredCustomerId(customerId);
        clickService.markClicked(recommendationId, resolvedCustomerId);
        return ResponseEntity.ok(ApiResponse.success(null, "Recommendation clicked", TraceIdResolver.resolve(servletRequest)));
    }
}
