package com.communityott.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsExportResponse {

    @JsonProperty("contract_version")
    @Builder.Default
    private String contractVersion = "analytics-contract-v1";

    @JsonProperty("generated_at")
    private Instant generatedAt;

    @JsonProperty("from")
    private LocalDate from;

    @JsonProperty("to")
    private LocalDate to;

    @JsonProperty("page")
    private int page;

    @JsonProperty("size")
    private int size;

    @JsonProperty("total_records")
    private long totalRecords;

    @JsonProperty("total_pages")
    private int totalPages;

    @JsonProperty("has_next")
    private boolean hasNext;

    @JsonProperty("records")
    private List<AnalyticsExportRecordDto> records;
}
