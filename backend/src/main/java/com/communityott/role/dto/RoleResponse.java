package com.communityott.role.dto;

import com.communityott.permission.dto.PermissionResponse;
import com.communityott.role.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private String description;
    private boolean systemRole;
    private Instant createdAt;
    private Instant updatedAt;
    private Set<PermissionResponse> permissions;

    public static RoleResponse fromEntity(Role role) {
        if (role == null) return null;

        Set<PermissionResponse> perms = null;
        if (role.getRolePermissions() != null) {
            perms = role.getRolePermissions().stream()
                    .filter(rp -> rp.getPermission() != null)
                    .map(rp -> PermissionResponse.fromEntity(rp.getPermission()))
                    .collect(Collectors.toSet());
        }

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .systemRole(role.isSystemRole())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .permissions(perms)
                .build();
    }
}
