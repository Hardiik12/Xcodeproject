package com.communityott.content.dto;

import com.communityott.content.entity.ProcessingJobStatus;
import com.communityott.content.entity.ProcessingJobType;
import com.communityott.content.entity.VideoProcessingJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoProcessingJobResponse {

    private Long id;
    private Long videoAssetId;
    private ProcessingJobType jobType;
    private ProcessingJobStatus status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Integer priority;
    private String workerId;
    private String errorCode;
    private String errorMessage;
    private String mediaMetadataJson;
    private Long createdBy;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant failedAt;
    private Instant lastHeartbeatAt;
    private Instant updatedAt;
    private Long version;

    public static VideoProcessingJobResponse fromEntity(VideoProcessingJob job) {
        if (job == null) {
            return null;
        }
        return VideoProcessingJobResponse.builder()
                .id(job.getId())
                .videoAssetId(job.getVideoAsset() != null ? job.getVideoAsset().getId() : null)
                .jobType(job.getJobType())
                .status(job.getStatus())
                .attemptCount(job.getAttemptCount())
                .maxAttempts(job.getMaxAttempts())
                .priority(job.getPriority())
                .workerId(job.getWorkerId())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .mediaMetadataJson(job.getMediaMetadataJson())
                .createdBy(job.getCreatedBy())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .failedAt(job.getFailedAt())
                .lastHeartbeatAt(job.getLastHeartbeatAt())
                .updatedAt(job.getUpdatedAt())
                .version(job.getVersion())
                .build();
    }
}
