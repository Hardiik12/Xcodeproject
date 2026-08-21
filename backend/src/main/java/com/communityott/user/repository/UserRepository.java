package com.communityott.user.repository;

import com.communityott.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END " +
           "FROM UserRole ur " +
           "JOIN ur.role r " +
           "JOIN r.rolePermissions rp " +
           "JOIN rp.permission p " +
           "WHERE ur.user.id = :userId AND p.name = :permissionName")
    boolean existsByUserIdAndPermissionName(@Param("userId") Long userId, @Param("permissionName") String permissionName);

    @Query("SELECT DISTINCT p.name " +
           "FROM UserRole ur " +
           "JOIN ur.role r " +
           "JOIN r.rolePermissions rp " +
           "JOIN rp.permission p " +
           "WHERE ur.user.id = :userId")
    Set<String> findPermissionNamesByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT r.name " +
           "FROM UserRole ur " +
           "JOIN ur.role r " +
           "WHERE ur.user.id = :userId")
    Set<String> findRoleNamesByUserId(@Param("userId") Long userId);

    long countByStatus(com.communityott.user.entity.UserStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}

