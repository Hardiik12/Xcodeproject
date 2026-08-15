package com.communityott.auth.security;

import com.communityott.common.security.CommunityOttPrincipal;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserStatus;
import com.communityott.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Spring Security filter that extracts and validates Bearer JWT access tokens from the
 * Authorization header.
 *
 * <p>When a valid JWT token is found:
 * <ol>
 *   <li>The token signature, expiration, issuer, and audience are verified.</li>
 *   <li>The user account is loaded and status verified (must be ACTIVE).</li>
 *   <li>A {@link CommunityOttPrincipal} is instantiated and placed in {@link SecurityContextHolder}.</li>
 * </ol>
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();

            if (!token.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<Long> userIdOpt = jwtTokenService.extractUserId(token);

                if (userIdOpt.isPresent()) {
                    Long userId = userIdOpt.get();
                    User user = userRepository.findById(userId).orElse(null);

                    if (user != null) {
                        if (user.getStatus() == UserStatus.ACTIVE) {
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
                            log.debug("JWT: Successfully authenticated user ID [{}] via Bearer token", userId);
                        } else {
                            log.warn("JWT: Authentication rejected for user ID [{}] due to inactive status [{}]", userId, user.getStatus());
                        }
                    } else {
                        log.warn("JWT: User ID [{}] extracted from token not found in database", userId);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
