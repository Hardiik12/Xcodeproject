package com.communityott.user.repository;

import com.communityott.user.entity.UserRole;
import com.communityott.user.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Long userId);

    List<UserRole> findByRoleId(Long roleId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserRole ur WHERE ur.role.id = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserRole ur WHERE ur.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserRole ur WHERE ur.user.id = :userId AND ur.role.id = :roleId")
    void deleteByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.name = :roleName")
    long countByRoleName(@Param("roleName") String roleName);
}
