package com.communityott.content.processing;

import lombok.Builder;
import lombok.Getter;

import java.io.File;
import java.util.List;

@Getter
@Builder
public class HlsPackagingResult {

    private final String resolution;
    private final File playlistFile;
    private final File initSegmentFile;
    private final List<File> mediaSegmentFiles;
    private final int segmentCount;
    private final int targetDurationSeconds;
    private final long bandwidthBps;
    private final Long averageBandwidthBps;
    private final String codecs;
    private final int width;
    private final int height;
    private final Double frameRate;
}
