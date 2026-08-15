package com.communityott.auth.dto;

import com.communityott.auth.entity.OtpPurpose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Service result DTO returned upon successful OTP verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerificationResult {

    private boolean verified;
    private Long userId;
    private String identifier;
    private OtpPurpose purpose;
}
