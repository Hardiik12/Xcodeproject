package com.communityott.auth.delivery;

import com.communityott.auth.entity.AuthIdentifierType;

/**
 * Specialized provider interface for SMS/Phone OTP delivery.
 */
public interface SmsOtpDeliveryProvider extends OtpDeliveryProvider {

    @Override
    default boolean supports(AuthIdentifierType type) {
        return type == AuthIdentifierType.PHONE;
    }
}
