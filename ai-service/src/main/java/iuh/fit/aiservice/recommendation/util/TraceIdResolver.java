package iuh.fit.aiservice.recommendation.util;

import iuh.fit.shared.trace.TraceIdConstants;
import iuh.fit.shared.trace.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;

public final class TraceIdResolver {

    private TraceIdResolver() {
    }

    public static String resolve(HttpServletRequest request) {
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
