package com.communityott.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentStatusSummaryResponse {

    private long draft;
    private long uploading;
    private long processing;
    private long ready;
    private long published;
    private long unpublished;
    private long failed;
    private long archived;
    private long total;
}
