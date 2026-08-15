package com.communityott.content.dto;

import java.util.List;

public record PublishabilityResult(
        boolean isPublishable,
        List<String> missingPrerequisites
) {
    public static PublishabilityResult success() {
        return new PublishabilityResult(true, List.of());
    }

    public static PublishabilityResult failure(List<String> missingPrerequisites) {
        return new PublishabilityResult(false, missingPrerequisites);
    }
}
