package com.communityott.content.service;

import com.communityott.content.dto.PublishabilityResult;
import com.communityott.content.entity.Content;
import com.communityott.content.entity.ContentStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultContentPublishabilityChecker implements ContentPublishabilityChecker {

    @Override
    public PublishabilityResult checkPublishability(Content content) {
        List<String> missingPrerequisites = new ArrayList<>();

        if (content == null) {
            return PublishabilityResult.failure(List.of("Content does not exist"));
        }

        if (content.getStatus() != ContentStatus.READY && content.getStatus() != ContentStatus.UNPUBLISHED) {
            missingPrerequisites.add("Content must be in READY or UNPUBLISHED status before publishing (current status: " + content.getStatus() + ")");
        }

        if (content.getTitle() == null || content.getTitle().trim().isBlank()) {
            missingPrerequisites.add("Title must not be blank");
        }

        if (content.getContentType() == null) {
            missingPrerequisites.add("Content type is required");
        }

        if (content.getDurationSeconds() == null || content.getDurationSeconds() <= 0) {
            missingPrerequisites.add("Duration must be a positive integer");
        }

        if (missingPrerequisites.isEmpty()) {
            return PublishabilityResult.success();
        } else {
            return PublishabilityResult.failure(missingPrerequisites);
        }
    }
}
