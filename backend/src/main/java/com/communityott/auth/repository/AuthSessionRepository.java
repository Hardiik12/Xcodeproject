package com.communityott.auth.repository;

import com.communityott.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    Optional<AuthSession> findByPreviousRefreshTokenHash(String previousRefreshTokenHash);

    List<AuthSession> findByUserId(Long userId);

    @Query("SELECT s FROM AuthSession s WHERE s.user.id = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now ORDER BY s.lastUsedAt DESC")
    List<AuthSession> findActiveSessionsByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("SELECT s FROM AuthSession s WHERE s.user.id = :userId AND s.deviceId = :deviceId AND s.revokedAt IS NULL AND s.expiresAt > :now")
    Optional<AuthSession> findActiveSessionByUserIdAndDeviceId(@Param("userId") Long userId,
                                                                 @Param("deviceId") String deviceId,
                                                                 @Param("now") Instant now);

    Optional<AuthSession> findByIdAndUserId(Long id, Long userId);

    List<AuthSession> findAllByDeviceEntityIdAndRevokedAtIsNull(Long deviceEntityId);

    @Query("SELECT COUNT(s) FROM AuthSession s WHERE s.user.id = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now")
    long countActiveSessions(@Param("userId") Long userId, @Param("now") Instant now);
}
