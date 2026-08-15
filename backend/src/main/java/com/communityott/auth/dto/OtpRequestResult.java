package com.communityott.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Service result DTO returned upon a successful OTP request.
 *
 * <p>Production responses contain only safe metadata. Plaintext OTP is NEVER exposed
 * in production responses.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OtpRequestResult {

    private Long requestId;
    private long expiresInSeconds;
    private long resendAfterSeconds;

    /**
     * Development-only exposed OTP field used exclusively for local testing.
     * Null in production environments.
     */
    private String devExposedOtp;
}
