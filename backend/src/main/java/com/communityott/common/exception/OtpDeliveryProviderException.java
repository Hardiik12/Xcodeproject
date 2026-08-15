package com.communityott.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an underlying OTP delivery provider (e.g. Email / SMS vendor API) fails or times out.
 */
public class OtpDeliveryProviderException extends ApiException {

    public OtpDeliveryProviderException(String message) {
        super(message, HttpStatus.BAD_GATEWAY, "OTP_DELIVERY_PROVIDER_ERROR");
    }

    public OtpDeliveryProviderException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, "OTP_DELIVERY_PROVIDER_ERROR", cause);
    }
}
