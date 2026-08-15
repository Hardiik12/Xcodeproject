package com.communityott.auth.security;

import com.communityott.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing JWT Access Token generation, cryptographic signature validation,
 * and claim extraction using JJWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    /**
     * Generates a cryptographically signed, short-lived JWT Access Token for the specified User.
     *
     * @param user authenticated User entity
     * @return signed JWT token string
     */
    public String generateAccessToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User and user ID must not be null for JWT generation");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getAccessTokenTtlSeconds());
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(jti)
                .claim("userId", user.getId())
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates signature, issuer, audience, and expiration, then returns token claims.
     *
     * @param token JWT string
     * @return Claims payload if valid
     * @throws JwtException if the token is invalid, expired, malformed, or untrusted
     */
    public Claims validateAndExtractClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token string must not be null or blank");
        }

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtProperties.getIssuer())
                .requireAudience(jwtProperties.getAudience())
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
    }

    /**
     * Safely extracts the authenticated User ID from a valid JWT.
     *
     * @param token JWT string
     * @return Optional containing User ID if token is valid and contains a valid numeric subject/claim
     */
    public Optional<Long> extractUserId(String token) {
        try {
            Claims claims = validateAndExtractClaims(token);
            String subject = claims.getSubject();
            if (subject != null && !subject.isBlank()) {
                return Optional.of(Long.parseLong(subject));
            }
            Object userIdClaim = claims.get("userId");
            if (userIdClaim instanceof Number number) {
                return Optional.of(number.longValue());
            }
        } catch (NumberFormatException e) {
            log.warn("JWT subject could not be parsed as numeric user ID");
        } catch (ExpiredJwtException e) {
            log.debug("JWT access token is expired");
        } catch (SignatureException e) {
            log.warn("JWT signature verification failed");
        } catch (MissingClaimException | IncorrectClaimException e) {
            log.warn("JWT claim validation failed: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT token parsing failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Safely extracts the unique Token Identifier (jti) from a valid JWT.
     *
     * @param token JWT string
     * @return Optional containing jti if token is valid
     */
    public Optional<String> extractJti(String token) {
        try {
            Claims claims = validateAndExtractClaims(token);
            return Optional.ofNullable(claims.getId());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Evaluates if a given JWT string is cryptographically valid, active, and meets all claim criteria.
     *
     * @param token JWT string
     * @return true if valid and active, false otherwise
     */
    public boolean isTokenValid(String token) {
        return extractUserId(token).isPresent();
    }

    /**
     * Constructs the SecretKey for HMAC-SHA256 signing.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
