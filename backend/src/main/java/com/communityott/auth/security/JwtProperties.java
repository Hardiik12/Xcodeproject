package com.communityott.auth.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "communityott.security.jwt")
public class JwtProperties {

    /**
     * Expected JWT token issuer claim ("iss").
     */
    private String issuer = "communityott";

    /**
     * Expected JWT token audience claim ("aud").
     */
    private String audience = "communityott-api";

    /**
     * Lifetime in seconds for short-lived access tokens (default: 900 seconds = 15 minutes).
     */
    private long accessTokenTtlSeconds = 900;

    /**
     * Cryptographic HMAC signing secret key (minimum 256 bits for HS256).
     */
    private String secret = "communityott_dev_jwt_hmac_secret_key_minimum_256_bits_for_security!";
}
