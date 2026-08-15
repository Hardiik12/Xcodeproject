package com.communityott.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Structured Redis payload object stored during an active OTP lifecycle.
 *
 * <p>Never contains the plaintext OTP string.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRedisData implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long requestId;
    private String otpHash;
    private int attemptCount;
    private Instant createdAt;
    private Instant expiresAt;
}
