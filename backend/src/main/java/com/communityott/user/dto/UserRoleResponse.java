package com.communityott.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleResponse {

    private Long userId;
    private Long roleId;
    private String roleName;
    private boolean systemRole;
}
