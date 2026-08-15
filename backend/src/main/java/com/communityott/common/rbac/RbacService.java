package com.communityott.common.rbac;

import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RbacService {

    private final UserRepository userRepository;

    public boolean hasPermission(Long userId, String permissionName) {
        if (userId == null || permissionName == null || permissionName.isBlank()) {
            return false;
        }

        String normalizedPermission = permissionName.trim();
        return userRepository.existsByUserIdAndPermissionName(userId, normalizedPermission);
    }

    public Set<String> getUserPermissions(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }

        return userRepository.findPermissionNamesByUserId(userId);
    }

    public Set<String> getUserRoles(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }

        return userRepository.findRoleNamesByUserId(userId);
    }
}
