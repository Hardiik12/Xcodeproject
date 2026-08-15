package com.communityott.common.config;

import com.communityott.common.security.CustomAccessDeniedHandler;
import com.communityott.common.security.CustomAuthenticationEntryPoint;
import com.communityott.common.security.DevAuthenticationFilter;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Main Spring Security Configuration for CommunityOTT Monolithic Backend.
 *
 * <p>Uses Spring Security 6 / Spring Boot 3.3 functional DSL configuration.
 * Enforces stateless security, fine-grained permission authorization, custom JSON
 * 401/403 error handling, and development-only header authentication.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final UserRepository userRepository;
    private final Environment environment;
    private final com.communityott.auth.security.JwtTokenService jwtTokenService;

    @Value("${communityott.security.dev-auth-enabled:false}")
    private boolean devAuthEnabled;

    @Value("${communityott.security.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                /*
                 * CSRF Configuration:
                 * CSRF is disabled because CommunityOTT backend is a stateless REST API designed
                 * for mobile (iOS/Android) and modern SPA web clients using header-based authentication
                 * (X-Dev-User-Id in development, Bearer JWT tokens in Phase 4). No browser HTTP session
                 * cookies are used for authentication.
                 */
                .csrf(AbstractHttpConfigurer::disable)

                /*
                 * CORS Configuration:
                 * Restricts cross-origin requests to configured client domains (iOS/Android dev environments,
                 * Web Admin portal) without permissive wildcard origins in production.
                 */
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                /*
                 * Session Management:
                 * Enforces strict STATELESS session policy. No HTTP sessions are created or used
                 * by Spring Security.
                 */
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                /*
                 * Exception Handling:
                 * Intercepts 401 (Unauthorized) and 403 (Forbidden) security exceptions to return
                 * standardized application JSON responses matching ErrorResponse.
                 */
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                /*
                 * Request Authorization Rules:
                 * Defines public endpoints for health checks and OpenAPI documentation, while requiring
                 * authentication for all business and API endpoints.
                 */
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/health",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/auth/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                /*
                 * JWT & Development Authentication Filters:
                 * 1. JwtAuthenticationFilter validates Authorization: Bearer <JWT> headers.
                 * 2. DevAuthenticationFilter evaluates X-Dev-User-Id fallback only when devAuthEnabled=true and active profile is local/dev/test.
                 */
                .addFilterBefore(
                        new com.communityott.auth.security.JwtAuthenticationFilter(jwtTokenService, userRepository),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        new DevAuthenticationFilter(userRepository, environment, devAuthEnabled),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Dev-User-Id", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Location", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
