package com.communityott.auth.dto;

import com.communityott.user.entity.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authenticated user profile metadata")
public class AuthenticatedUserResponse {

    @Schema(description = "User unique primary key ID", example = "1")
    private Long id;

    @Schema(description = "User email address", example = "user@example.com")
    private String email;

    @Schema(description = "User mobile phone number", example = "+15551234567")
    private String phone;

    @Schema(description = "User display name", example = "Hardik")
    private String displayName;

    @Schema(description = "User account status (ACTIVE, SUSPENDED, DELETED)", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Assigned RBAC role names", example = "[\"USER\"]")
    private Set<String> roles;
}
