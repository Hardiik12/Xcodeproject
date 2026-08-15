package com.communityott.auth.repository;

import com.communityott.auth.entity.OtpPurpose;
import com.communityott.auth.entity.OtpRequest;
import com.communityott.auth.entity.OtpRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {

    List<OtpRequest> findByUserId(Long userId);

    List<OtpRequest> findByIdentifier(String identifier);

    Optional<OtpRequest> findByIdentifierAndPurposeAndStatus(String identifier, OtpPurpose purpose, OtpRequestStatus status);

    @Query("SELECT o FROM OtpRequest o WHERE o.identifier = :identifier AND o.purpose = :purpose AND o.status = 'REQUESTED' AND o.expiresAt > :now ORDER BY o.createdAt DESC")
    List<OtpRequest> findActiveRequests(@Param("identifier") String identifier,
                                         @Param("purpose") OtpPurpose purpose,
                                         @Param("now") Instant now);

    @Query("SELECT COUNT(o) FROM OtpRequest o WHERE o.identifier = :identifier AND o.createdAt >= :since")
    long countRecentRequests(@Param("identifier") String identifier,
                             @Param("since") Instant since);
}
