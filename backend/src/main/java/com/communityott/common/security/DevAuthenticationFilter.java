package com.communityott.common.security;

import com.communityott.user.entity.User;
import com.communityott.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * DEVELOPMENT ONLY Authentication Filter.
 *
 * <p>Reads the {@code X-Dev-User-Id} request header to authenticate users during local development
 * prior to the implementation of JWT/OTP authentication in Phase 4.</p>
 *
 * <p><strong>CRITICAL SECURITY GUARD:</strong></p>
 * <ul>
 *   <li>This filter is strictly active ONLY when {@code communityott.security.dev-auth-enabled=true}</li>
 *   <li>AND the active profile is {@code local} or {@code dev}.</li>
 *   <li>In production or when dev-auth is disabled, all {@code X-Dev-User-Id} headers are IGNORED.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class DevAuthenticationFilter extends OncePerRequestFilter {

    public static final String DEV_USER_ID_HEADER = "X-Dev-User-Id";

    private final UserRepository userRepository;
    private final Environment environment;
    private final boolean devAuthEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!isDevAuthPermitted()) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerValue = request.getHeader(DEV_USER_ID_HEADER);
        if (headerValue != null && !headerValue.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Long userId = Long.parseLong(headerValue.trim());
                User user = userRepository.findById(userId).orElse(null);

                if (user != null) {
                    CommunityOttPrincipal principal = CommunityOttPrincipal.builder()
                            .userId(user.getId())
                            .email(user.getEmail())
                            .displayName(user.getDisplayName())
                            .build();

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("DevAuth: Successfully authenticated user ID {}", userId);
                } else {
                    log.debug("DevAuth: User ID {} from header not found in database", userId);
                }
            } catch (NumberFormatException e) {
                log.debug("DevAuth: Invalid non-numeric X-Dev-User-Id header value");
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Verifies whether development authentication is permitted by checking both property configuration
     * and active profiles.
     */
    private boolean isDevAuthPermitted() {
        if (!devAuthEnabled) {
            return false;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles == null || activeProfiles.length == 0) {
            return false;
        }

        return Arrays.stream(activeProfiles)
                .anyMatch(profile -> "local".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile));
    }
}
