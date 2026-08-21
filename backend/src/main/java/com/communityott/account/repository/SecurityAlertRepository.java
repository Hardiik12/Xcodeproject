package com.communityott.account.repository;

import com.communityott.account.entity.SecurityAlert;
import com.communityott.account.model.SecurityAlertStatus;
import com.communityott.account.model.SecurityAlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing user-scoped security alerts.
 */
@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long>, JpaSpecificationExecutor<SecurityAlert> {

    Page<SecurityAlert> findByUserId(Long userId, Pageable pageable);

    Page<SecurityAlert> findByUserIdAndStatus(Long userId, SecurityAlertStatus status, Pageable pageable);

    long countByUserIdAndStatus(Long userId, SecurityAlertStatus status);

    Optional<SecurityAlert> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE SecurityAlert a SET a.status = :readStatus, a.readAt = :readAt WHERE a.user.id = :userId AND a.status = :unreadStatus")
    int markAllUnreadAsRead(
            @Param("userId") Long userId,
            @Param("unreadStatus") SecurityAlertStatus unreadStatus,
            @Param("readStatus") SecurityAlertStatus readStatus,
            @Param("readAt") Instant readAt
    );

    boolean existsByUserIdAndAlertTypeAndSourceEventId(Long userId, SecurityAlertType alertType, UUID sourceEventId);

    boolean existsByUserIdAndAlertTypeAndCreatedAtAfter(Long userId, SecurityAlertType alertType, Instant windowStart);

    @Modifying
    @Query("DELETE FROM SecurityAlert a WHERE a.expiresAt < :now")
    int deleteExpiredAlerts(@Param("now") Instant now);
}
