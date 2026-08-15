package com.communityott.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleCreateRequest {

    @NotBlank(message = "Role name must not be blank")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    private String name;

    @Size(max = 255, message = "Role description must not exceed 255 characters")
    private String description;
}
