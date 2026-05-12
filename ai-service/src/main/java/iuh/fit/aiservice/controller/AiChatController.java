package iuh.fit.aiservice.controller;

import iuh.fit.aiservice.dto.request.AiChatRequest;
import iuh.fit.aiservice.dto.response.AiChatResponse;
import iuh.fit.aiservice.dto.response.AiChatMessageItem;
import iuh.fit.aiservice.dto.response.AiChatSessionSummary;
import iuh.fit.aiservice.dto.response.CursorPageResponse;
import iuh.fit.aiservice.service.AiChatService;
import iuh.fit.aiservice.service.AiChatHistoryService;
import iuh.fit.shared.api.ApiResponse;
import iuh.fit.shared.trace.TraceIdConstants;
import iuh.fit.shared.trace.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@Validated
public class AiChatController {

    private final AiChatService chatService;
    private final AiChatHistoryService historyService;

    public AiChatController(AiChatService chatService, AiChatHistoryService historyService) {
        this.chatService = chatService;
        this.historyService = historyService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest servletRequest
    ) {
        AiChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Chat response generated", resolveTraceId(servletRequest)));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<CursorPageResponse<AiChatSessionSummary>>> listSessions(
            @NotBlank @RequestParam String customerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest servletRequest
    ) {
        CursorPageResponse<AiChatSessionSummary> response = historyService.listSessions(customerId, cursor, limit);
        return ResponseEntity.ok(ApiResponse.success(response, "Chat sessions fetched", resolveTraceId(servletRequest)));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<CursorPageResponse<AiChatMessageItem>>> listMessages(
            @NotBlank @RequestParam String customerId,
            @PathVariable String sessionId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest servletRequest
    ) {
        CursorPageResponse<AiChatMessageItem> response = historyService.listMessages(customerId, sessionId, cursor, limit);
        return ResponseEntity.ok(ApiResponse.success(response, "Chat messages fetched", resolveTraceId(servletRequest)));
    }

    private static String resolveTraceId(HttpServletRequest request) {
        if (request != null) {
            Object attr = request.getAttribute(TraceIdConstants.REQUEST_ATTRIBUTE);
            if (attr instanceof String traceId && !traceId.isBlank()) {
                return traceId;
            }

            String headerTraceId = request.getHeader(TraceIdConstants.HEADER_NAME);
            if (headerTraceId != null && !headerTraceId.isBlank()) {
                return headerTraceId;
            }
        }

        String contextTraceId = TraceIdContext.get();
        return (contextTraceId == null || contextTraceId.isBlank()) ? null : contextTraceId;
    }
}