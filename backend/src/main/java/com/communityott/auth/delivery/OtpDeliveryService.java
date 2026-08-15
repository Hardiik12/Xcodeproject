package com.communityott.auth.delivery;

import com.communityott.auth.dto.OtpDeliveryResult;
import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.auth.entity.OtpDeliveryAttempt;
import com.communityott.auth.entity.OtpDeliveryStatus;
import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.OtpRequest;
import com.communityott.auth.repository.OtpDeliveryAttemptRepository;
import com.communityott.auth.repository.OtpRequestRepository;
import com.communityott.common.exception.OtpDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service responsible for orchestrating OTP delivery across specialized providers (Email/SMS)
 * and persisting delivery attempt audit records.
 *
 * <p>Never logs, persists, or returns the plaintext OTP code.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpDeliveryService {

    private final List<OtpDeliveryProvider> providers;
    private final OtpDeliveryAttemptRepository deliveryAttemptRepository;
    private final OtpRequestRepository otpRequestRepository;

    @Value("${communityott.auth.delivery.mode:development}")
    private String deliveryMode;

    @Value("${communityott.auth.delivery.timeout-ms:5000}")
    private long timeoutMs;

    /**
     * Dispatches an OTP via the appropriate delivery provider and records the delivery attempt.
     *
     * @param identifierType EMAIL or PHONE
     * @param identifier normalized target identifier (email or phone)
     * @param plaintextOtp 6-digit OTP code (in-memory only)
     * @param purpose OTP request purpose
     * @param otpRequestId associated PostgreSQL otp_requests record ID
     * @return safe OtpDeliveryResult with delivery metadata
     */
    @Transactional
    public OtpDeliveryResult deliverOtp(AuthIdentifierType identifierType, String identifier, String plaintextOtp, OtpPurpose purpose, Long otpRequestId) {
        OtpDeliveryProvider provider = selectProvider(identifierType);

        OtpRequest otpRequest = otpRequestRepository.findById(otpRequestId)
                .orElseThrow(() -> new OtpDeliveryException("Associated OTP request not found for ID: " + otpRequestId));

        // 1. Record initial PENDING attempt in PostgreSQL
        OtpDeliveryAttempt attempt = OtpDeliveryAttempt.builder()
                .otpRequest(otpRequest)
                .channel(identifierType)
                .provider(provider.getProviderName())
                .status(OtpDeliveryStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        OtpDeliveryAttempt savedAttempt = deliveryAttemptRepository.save(attempt);

        // 2. Invoke Provider Delivery
        try {
            OtpDeliveryResult result = provider.send(identifier, plaintextOtp, purpose);

            if (result != null && result.isSuccess()) {
                savedAttempt.setStatus(OtpDeliveryStatus.SENT);
                savedAttempt.setProviderMessageId(result.getDeliveryId());
                savedAttempt.setDeliveredAt(Instant.now());
                deliveryAttemptRepository.save(savedAttempt);

                log.info("OTP successfully dispatched for request ID {} via provider {}", otpRequestId, provider.getProviderName());
                return result;
            } else {
                String failureCode = result != null ? result.getFailureCode() : "UNKNOWN_ERROR";
                savedAttempt.setStatus(OtpDeliveryStatus.FAILED);
                savedAttempt.setFailureCode(failureCode);
                deliveryAttemptRepository.save(savedAttempt);

                log.error("OTP delivery rejected by provider {} for request ID {}", provider.getProviderName(), otpRequestId);
                throw new OtpDeliveryException("OTP delivery failed via provider " + provider.getProviderName());
            }
        } catch (Exception e) {
            savedAttempt.setStatus(OtpDeliveryStatus.FAILED);
            savedAttempt.setFailureCode(e.getClass().getSimpleName());
            deliveryAttemptRepository.save(savedAttempt);

            log.error("Exception during OTP dispatch for request ID {} via provider {}: {}", otpRequestId, provider.getProviderName(), e.getMessage());
            if (e instanceof OtpDeliveryException ode) {
                throw ode;
            }
            throw new OtpDeliveryException("Failed to deliver OTP to the recipient", e);
        }
    }

    private OtpDeliveryProvider selectProvider(AuthIdentifierType type) {
        return providers.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new OtpDeliveryException("No delivery provider registered for channel: " + type));
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }
}
