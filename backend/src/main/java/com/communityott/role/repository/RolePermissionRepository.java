package com.communityott.role.repository;

import com.communityott.role.entity.RolePermission;
import com.communityott.role.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByRoleId(Long roleId);

    List<RolePermission> findByPermissionId(Long permissionId);

    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RolePermission rp WHERE rp.role.id = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RolePermission rp WHERE rp.permission.id = :permissionId")
    void deleteByPermissionId(@Param("permissionId") Long permissionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RolePermission rp WHERE rp.role.id = :roleId AND rp.permission.id = :permissionId")
    void deleteByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
