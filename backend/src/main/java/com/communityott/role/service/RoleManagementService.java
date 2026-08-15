package com.communityott.role.service;

import com.communityott.common.exception.PermissionNotFoundException;
import com.communityott.common.exception.RoleAlreadyExistsException;
import com.communityott.common.exception.RoleNotFoundException;
import com.communityott.common.exception.SystemRoleModificationException;
import com.communityott.permission.entity.Permission;
import com.communityott.permission.repository.PermissionRepository;
import com.communityott.role.dto.RoleCreateRequest;
import com.communityott.role.dto.RoleResponse;
import com.communityott.role.dto.RoleUpdateRequest;
import com.communityott.role.entity.Role;
import com.communityott.role.entity.RolePermission;
import com.communityott.role.entity.RolePermissionId;
import com.communityott.role.repository.RolePermissionRepository;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    public RoleResponse createRole(RoleCreateRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Role name must not be blank");
        }

        String normalizedName = request.getName().trim().toUpperCase();

        if (roleRepository.existsByName(normalizedName)) {
            throw new RoleAlreadyExistsException(normalizedName);
        }

        Role role = Role.builder()
                .name(normalizedName)
                .description(request.getDescription())
                .isSystemRole(false)
                .build();

        Role savedRole = roleRepository.save(role);
        log.info("Created custom role: [{}] ID: {}", savedRole.getName(), savedRole.getId());
        return RoleResponse.fromEntity(savedRole);
    }

    public RoleResponse updateRole(Long roleId, RoleUpdateRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (request == null) {
            return RoleResponse.fromEntity(role);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            String normalizedName = request.getName().trim().toUpperCase();
            if (!normalizedName.equals(role.getName())) {
                if (role.isSystemRole()) {
                    throw new SystemRoleModificationException("System roles cannot be renamed: " + role.getName());
                }
                if (roleRepository.existsByName(normalizedName)) {
                    throw new RoleAlreadyExistsException(normalizedName);
                }
                role.setName(normalizedName);
            }
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        Role updatedRole = roleRepository.save(role);
        log.info("Updated role ID {}: {}", roleId, updatedRole.getName());
        return RoleResponse.fromEntity(updatedRole);
    }

    public void deleteRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (role.isSystemRole()) {
            throw new SystemRoleModificationException("System role cannot be deleted: " + role.getName());
        }

        // Delete join table relationships first
        userRoleRepository.deleteByRoleId(roleId);
        role.getRolePermissions().clear();

        roleRepository.delete(role);
        roleRepository.flush();
        log.info("Deleted custom role ID {}: {}", roleId, role.getName());
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        return RoleResponse.fromEntity(role);
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleByName(String name) {
        Role role = roleRepository.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException("Role not found with name: " + name));
        return RoleResponse.fromEntity(role);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(RoleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public RoleResponse assignPermissionToRole(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new PermissionNotFoundException(permissionId));

        if (!rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            RolePermission rolePermission = new RolePermission(role, permission);
            role.getRolePermissions().add(rolePermission);
            roleRepository.save(role);
            log.info("Assigned permission [{}] to role [{}]", permission.getName(), role.getName());
        }

        return RoleResponse.fromEntity(role);
    }

    public RoleResponse removePermissionFromRole(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new PermissionNotFoundException(permissionId));

        if ("SUPER_ADMIN".equalsIgnoreCase(role.getName())) {
            throw new SystemRoleModificationException("Permissions cannot be removed from SUPER_ADMIN role");
        }

        RolePermissionId id = new RolePermissionId(roleId, permissionId);
        if (rolePermissionRepository.existsById(id)) {
            rolePermissionRepository.deleteById(id);
            rolePermissionRepository.flush();
            role.getRolePermissions().removeIf(rp -> rp.getPermission().getId().equals(permissionId));
            log.info("Removed permission [{}] from role [{}]", permission.getName(), role.getName());
        }

        return RoleResponse.fromEntity(role);
    }
}
