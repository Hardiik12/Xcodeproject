package com.communityott.content.processing;

import com.communityott.content.entity.VideoRendition;

import java.io.File;

public interface FFmpegHlsPackagingService {

    /**
     * Packages an encoded MP4 rendition into fMP4/CMAF HLS segments and a variant playlist.
     * Uses stream copy (-c copy) to avoid re-encoding.
     *
     * @param sourceMp4      The source rendition MP4 file
     * @param targetOutputDir The directory where index.m3u8, init.mp4, and .m4s segments are written
     * @param rendition      The rendition metadata
     * @param targetDurationSeconds Target segment duration (default: 2)
     * @return HlsPackagingResult describing generated files and playlist metadata
     */
    HlsPackagingResult packageToHls(File sourceMp4, File targetOutputDir, VideoRendition rendition, int targetDurationSeconds);
}
