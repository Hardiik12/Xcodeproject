package com.communityott.permission.dto;

import com.communityott.permission.entity.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {

    private Long id;
    private String name;
    private String description;
    private String module;
    private String action;
    private Instant createdAt;

    public static PermissionResponse fromEntity(Permission permission) {
        if (permission == null) return null;
        return PermissionResponse.builder()
                .id(permission.getId())
                .name(permission.getName())
                .description(permission.getDescription())
                .module(permission.getModule())
                .action(permission.getAction())
                .createdAt(permission.getCreatedAt())
                .build();
    }
}
