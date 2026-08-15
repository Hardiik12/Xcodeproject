package com.communityott.auth.repository;

import com.communityott.auth.entity.OtpDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OtpDeliveryAttemptRepository extends JpaRepository<OtpDeliveryAttempt, Long> {

    List<OtpDeliveryAttempt> findByOtpRequestId(Long otpRequestId);
}
