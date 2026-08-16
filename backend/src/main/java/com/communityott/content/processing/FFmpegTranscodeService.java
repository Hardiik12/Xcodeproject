package com.communityott.content.processing;

import java.io.File;

/**
 * Service for executing multi-resolution FFmpeg transcoding operations.
 */
public interface FFmpegTranscodeService {

    /**
     * Transcodes a source video file into a target rendition matching the given profile.
     *
     * @param sourceFile local source video file
     * @param targetFile local destination file to write encoded MP4
     * @param profile    transcoding parameters (resolution, bitrates, codecs, scale)
     * @return true if transcoding succeeded and target file exists with non-zero size
     */
    boolean transcode(File sourceFile, File targetFile, TranscodeProfile profile);
}
