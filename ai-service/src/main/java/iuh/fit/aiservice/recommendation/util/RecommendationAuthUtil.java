package iuh.fit.aiservice.recommendation.util;

import iuh.fit.shared.error.BusinessException;
import iuh.fit.shared.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

public final class RecommendationAuthUtil {

    private RecommendationAuthUtil() {
    }

    public static UUID resolveOptionalCustomerId(String requestCustomerId) {
        UUID fromRequest = parseUuid(requestCustomerId, false);
        String subject = resolveJwtSubject();
        UUID fromToken = parseUuid(subject, false);

        if (fromRequest != null && fromToken != null && !fromRequest.equals(fromToken)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Customer id does not match authenticated user");
        }

        if (fromToken != null) {
            return fromToken;
        }

        return fromRequest;
    }

    public static UUID resolveRequiredCustomerId(String requestCustomerId) {
        UUID resolved = resolveOptionalCustomerId(requestCustomerId);
        if (resolved == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing customer id");
        }
        return resolved;
    }

    private static String resolveJwtSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        return null;
    }

    private static UUID parseUuid(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid customer id");
            }
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid customer id");
        }
    }
}
