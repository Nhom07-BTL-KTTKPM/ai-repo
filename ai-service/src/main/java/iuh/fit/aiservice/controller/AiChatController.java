package iuh.fit.aiservice.controller;

import iuh.fit.aiservice.dto.request.AiChatRequest;
import iuh.fit.aiservice.dto.response.AiChatResponse;
import iuh.fit.aiservice.service.AiChatService;
import iuh.fit.shared.api.ApiResponse;
import iuh.fit.shared.trace.TraceIdConstants;
import iuh.fit.shared.trace.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final AiChatService chatService;

    public AiChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest servletRequest
    ) {
        AiChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Chat response generated", resolveTraceId(servletRequest)));
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