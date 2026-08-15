package com.communityott.user.service;

import com.communityott.common.exception.LastSuperAdminProtectionException;
import com.communityott.common.exception.RoleNotFoundException;
import com.communityott.common.exception.UserNotFoundException;
import com.communityott.common.rbac.RbacService;
import com.communityott.role.entity.Role;
import com.communityott.role.repository.RoleRepository;
import com.communityott.user.dto.UserRoleResponse;
import com.communityott.user.entity.User;
import com.communityott.user.entity.UserRole;
import com.communityott.user.repository.UserRepository;
import com.communityott.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserRoleManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RbacService rbacService;

    public UserRoleResponse assignRoleToUser(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (!userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            UserRole userRole = new UserRole(user, role);
            userRoleRepository.save(userRole);
            log.info("Assigned role [{}] to user ID [{}]", role.getName(), userId);
        }

        return UserRoleResponse.builder()
                .userId(userId)
                .roleId(roleId)
                .roleName(role.getName())
                .systemRole(role.isSystemRole())
                .build();
    }

    public void removeRoleFromUser(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if ("SUPER_ADMIN".equalsIgnoreCase(role.getName())) {
            long superAdminCount = userRoleRepository.countByRoleName("SUPER_ADMIN");
            if (superAdminCount <= 1) {
                throw new LastSuperAdminProtectionException();
            }
        }

        com.communityott.user.entity.UserRoleId id = new com.communityott.user.entity.UserRoleId(userId, roleId);
        if (userRoleRepository.existsById(id)) {
            userRoleRepository.deleteById(id);
            userRoleRepository.flush();
            log.info("Removed role [{}] from user ID [{}]", role.getName(), userId);
        }
    }

    @Transactional(readOnly = true)
    public Set<String> getUserRoles(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return rbacService.getUserRoles(userId);
    }

    @Transactional(readOnly = true)
    public Set<String> getUserEffectivePermissions(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return rbacService.getUserPermissions(userId);
    }
}
