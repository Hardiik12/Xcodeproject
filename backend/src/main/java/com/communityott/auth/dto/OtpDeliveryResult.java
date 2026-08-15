package com.communityott.auth.dto;

import com.communityott.auth.entity.AuthIdentifierType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Safe result DTO returned by OTP delivery operations.
 *
 * <p>NEVER contains plaintext OTP, OTP hash, secret keys, or provider credentials.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OtpDeliveryResult {

    private boolean success;
    private AuthIdentifierType channel;
    private String provider;
    private String deliveryId;
    private Instant timestamp;
    private String failureCode;
}
