package com.communityott.auth.delivery;

import com.communityott.auth.entity.AuthIdentifierType;

/**
 * Specialized provider interface for Email OTP delivery.
 */
public interface EmailOtpDeliveryProvider extends OtpDeliveryProvider {

    @Override
    default boolean supports(AuthIdentifierType type) {
        return type == AuthIdentifierType.EMAIL;
    }
}
