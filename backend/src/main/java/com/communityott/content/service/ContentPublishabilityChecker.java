package com.communityott.content.service;

import com.communityott.content.dto.PublishabilityResult;
import com.communityott.content.entity.Content;

public interface ContentPublishabilityChecker {

    PublishabilityResult checkPublishability(Content content);
}
