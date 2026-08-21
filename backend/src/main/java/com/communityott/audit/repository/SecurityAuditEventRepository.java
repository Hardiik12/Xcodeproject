package com.communityott.audit.repository;

import com.communityott.audit.entity.SecurityAuditEvent;
import com.communityott.audit.model.SecurityEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-Only Repository for Security Audit Events.
 */
@Repository
public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {

    Optional<SecurityAuditEvent> findByEventId(UUID eventId);

    Page<SecurityAuditEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<SecurityAuditEvent> findByUserIdAndEventTypeOrderByCreatedAtDesc(Long userId, SecurityEventType eventType);

    @Query("SELECT e FROM SecurityAuditEvent e WHERE (:userId IS NULL OR e.user.id = :userId) " +
           "AND (:eventType IS NULL OR e.eventType = :eventType) " +
           "AND (:fromDate IS NULL OR e.createdAt >= :fromDate) " +
           "AND (:toDate IS NULL OR e.createdAt <= :toDate) ORDER BY e.createdAt DESC")
    Page<SecurityAuditEvent> searchAuditEvents(
            @Param("userId") Long userId,
            @Param("eventType") SecurityEventType eventType,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );
}
