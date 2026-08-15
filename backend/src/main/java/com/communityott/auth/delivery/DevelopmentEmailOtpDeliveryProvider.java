package com.communityott.auth.delivery;

import com.communityott.auth.dto.OtpDeliveryResult;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development implementation of EmailOtpDeliveryProvider.
 * Enables end-to-end local testing without external email vendor credentials.
 *
 * <p>Plaintext OTPs are NEVER logged. For unit/integration tests, a thread-safe in-memory
 * fixture lookup is provided.</p>
 */
@Slf4j
@Component
public class DevelopmentEmailOtpDeliveryProvider implements EmailOtpDeliveryProvider {

    public static final String PROVIDER_NAME = "DEV_EMAIL";

    // Test-only memory store for verification in test environments
    private final Map<String, String> testOtpStore = new ConcurrentHashMap<>();

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public OtpDeliveryResult send(String identifier, String otp, OtpPurpose purpose) {
        log.info("Simulating EMAIL OTP delivery via {} for purpose: {}", PROVIDER_NAME, purpose);

        // Store for testing assertions only
        if (identifier != null && otp != null) {
            testOtpStore.put(identifier, otp);
        }

        String deliveryId = "dev-email-" + UUID.randomUUID();

        return OtpDeliveryResult.builder()
                .success(true)
                .channel(AuthIdentifierType.EMAIL)
                .provider(PROVIDER_NAME)
                .deliveryId(deliveryId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Test-fixture helper method to query the delivered OTP in automated tests.
     */
    public String getLastDeliveredOtp(String identifier) {
        return testOtpStore.get(identifier);
    }

    public void clearTestStore() {
        testOtpStore.clear();
    }
}
