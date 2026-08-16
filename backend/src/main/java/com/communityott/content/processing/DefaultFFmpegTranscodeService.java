package com.communityott.content.processing;

import com.communityott.common.exception.VideoProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultFFmpegTranscodeService implements FFmpegTranscodeService {

    private final ProcessRunner processRunner;
    private final FFmpegProperties properties;

    @Override
    public boolean transcode(File sourceFile, File targetFile, TranscodeProfile profile) {
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0) {
            throw new VideoProcessingException("Invalid source file for transcoding: " + (sourceFile == null ? "null" : sourceFile.getAbsolutePath()));
        }

        if (targetFile == null) {
            throw new VideoProcessingException("Target file destination cannot be null");
        }

        // Ensure target parent directory exists
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        List<String> command = buildFfmpegCommand(sourceFile, targetFile, profile);
        log.info("Starting FFmpeg transcoding for rendition {} using command: {}", profile.getResolution().getLabel(), String.join(" ", command));

        ProcessExecutionResult result = processRunner.execute(command, properties.getTimeoutSeconds());

        if (!result.isSuccess()) {
            log.error("FFmpeg transcoding failed for rendition {}. Exit code: {}. Stderr: {}",
                    profile.getResolution().getLabel(), result.exitCode(), result.stderr());
            throw new VideoProcessingException("FFmpeg transcoding failed for " + profile.getResolution().getLabel() + ": " + result.stderr());
        }

        if (!targetFile.exists() || targetFile.length() == 0) {
            throw new VideoProcessingException("FFmpeg succeeded with exit code 0 but target file is missing or empty: " + targetFile.getAbsolutePath());
        }

        log.info("FFmpeg transcoding completed successfully for rendition {}. Output size: {} bytes",
                profile.getResolution().getLabel(), targetFile.length());
        return true;
    }

    private List<String> buildFfmpegCommand(File sourceFile, File targetFile, TranscodeProfile profile) {
        List<String> command = new ArrayList<>();
        command.add(properties.getFfmpegPath());
        command.add("-y");
        command.add("-i");
        command.add(sourceFile.getAbsolutePath());

        // Video scale filter: ensures width is even (-2) while scaling to exact target height
        command.add("-vf");
        command.add("scale=-2:" + profile.getHeight());

        // Video codec & pixel format
        command.add("-c:v");
        command.add(profile.getVideoCodec());
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-preset");
        command.add(profile.getPreset());

        // Bitrate control
        int videoBitrate = profile.getVideoBitrateKbps();
        command.add("-b:v");
        command.add(videoBitrate + "k");
        command.add("-maxrate");
        command.add((int) (videoBitrate * 1.2) + "k");
        command.add("-bufsize");
        command.add((videoBitrate * 2) + "k");

        // Audio codec & parameters
        command.add("-c:a");
        command.add(profile.getAudioCodec());
        command.add("-b:a");
        command.add(profile.getAudioBitrateKbps() + "k");
        command.add("-ar");
        command.add("48000");
        command.add("-ac");
        command.add("2");

        // Faststart for progressive web playback
        command.add("-movflags");
        command.add("+faststart");

        command.add(targetFile.getAbsolutePath());
        return command;
    }
}
