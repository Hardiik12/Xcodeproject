package com.communityott.content.dto;

import com.communityott.content.entity.ContentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for updating content lifecycle status")
public class UpdateContentStatusRequest {

    @NotNull(message = "Target status is required")
    @Schema(description = "Target lifecycle status", example = "PUBLISHED")
    private ContentStatus status;
}
