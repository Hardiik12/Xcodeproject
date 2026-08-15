package com.communityott.content.dto;

import com.communityott.content.entity.ContentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentStatusTransitionRequest {

    @NotNull(message = "Target status is required")
    private ContentStatus targetStatus;
}
