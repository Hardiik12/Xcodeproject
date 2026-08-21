package com.communityott.common.security;

import com.communityott.audit.dto.SecurityAuditEventPayload;
import com.communityott.audit.model.SecurityEventOutcome;
import com.communityott.audit.model.SecurityEventType;
import com.communityott.audit.publisher.SecurityAuditEventPublisher;
import com.communityott.auth.entity.Platform;
import com.communityott.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom Spring Security {@link AccessDeniedHandler} that handles 403 Forbidden responses.
 *
 * <p>Triggered when an authenticated user lacks the required permission for an endpoint.
 * Returns a standard JSON error response without exposing permission names or internal roles.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final SecurityAuditEventPublisher securityAuditEventPublisher;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        log.debug("Forbidden access attempt to {}: {}", request.getRequestURI(), accessDeniedException.getMessage());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = null;
        if (auth != null && auth.getPrincipal() instanceof CommunityOttPrincipal principal) {
            userId = principal.getUserId();
        }

        securityAuditEventPublisher.publish(SecurityAuditEventPayload.builder()
                .eventType(SecurityEventType.AUTHZ_DENIED)
                .outcome(SecurityEventOutcome.BLOCKED)
                .reasonCode("PERMISSION_DENIED")
                .userId(userId)
                .platform(Platform.WEB)
                .deviceIdentifier("authz-denied-client")
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .build());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(
                "FORBIDDEN",
                "You do not have permission to perform this action"
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
