package com.communityott.permission.service;

import com.communityott.common.exception.PermissionNotFoundException;
import com.communityott.permission.dto.PermissionResponse;
import com.communityott.permission.entity.Permission;
import com.communityott.permission.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionManagementService {

    private final PermissionRepository permissionRepository;

    public PermissionResponse getPermission(Long permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new PermissionNotFoundException(permissionId));
        return PermissionResponse.fromEntity(permission);
    }

    public PermissionResponse getPermissionByName(String name) {
        Permission permission = permissionRepository.findByName(name)
                .orElseThrow(() -> new PermissionNotFoundException("Permission not found with name: " + name));
        return PermissionResponse.fromEntity(permission);
    }

    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(PermissionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<PermissionResponse> getPermissionsByModule(String module) {
        return permissionRepository.findByModule(module).stream()
                .map(PermissionResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
