package com.communityott.device.repository;

import com.communityott.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByUserIdAndDeviceIdentifier(Long userId, String deviceIdentifier);

    Optional<Device> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndRevokedAtIsNull(Long userId);

    List<Device> findAllByUserIdAndRevokedAtIsNull(Long userId);

    List<Device> findAllByUserIdOrderByLastActiveAtDesc(Long userId);

    @Query("SELECT d FROM Device d WHERE d.user.id = :userId AND d.revokedAt IS NULL ORDER BY d.lastActiveAt DESC")
    List<Device> findActiveDevicesByUserId(@Param("userId") Long userId);
}
