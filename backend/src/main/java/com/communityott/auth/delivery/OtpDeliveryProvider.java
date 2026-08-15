package com.communityott.auth.delivery;

import com.communityott.auth.dto.OtpDeliveryResult;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpPurpose;

/**
 * Common abstraction for OTP delivery providers (Email, SMS, etc.).
 *
 * <p>Providers receive the plaintext OTP code strictly in-memory for immediate dispatch.
 * Providers MUST NOT persist, cache, or log the plaintext OTP.</p>
 */
public interface OtpDeliveryProvider {

    /**
     * Checks whether this provider supports the given identifier type (EMAIL or PHONE).
     *
     * @param type the identifier type
     * @return true if supported, false otherwise
     */
    boolean supports(AuthIdentifierType type);

    /**
     * Unique identifier for this provider implementation (e.g. "DEV_EMAIL", "DEV_SMS", "SES", "TWILIO").
     *
     * @return provider name string
     */
    String getProviderName();

    /**
     * Dispatches the OTP to the target recipient.
     *
     * @param identifier normalized target recipient (email or phone number)
     * @param otp plaintext OTP code (in-memory only)
     * @param purpose the purpose of the OTP request
     * @return safe OtpDeliveryResult with delivery metadata
     */
    OtpDeliveryResult send(String identifier, String otp, OtpPurpose purpose);
}
