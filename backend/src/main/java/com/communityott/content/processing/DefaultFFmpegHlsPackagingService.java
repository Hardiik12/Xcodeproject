package com.communityott.content.processing;

import com.communityott.common.exception.VideoProcessingException;
import com.communityott.content.entity.VideoRendition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultFFmpegHlsPackagingService implements FFmpegHlsPackagingService {

    private final ProcessRunner processRunner;
    private final FFmpegProperties properties;

    @Override
    public HlsPackagingResult packageToHls(File sourceMp4, File targetOutputDir, VideoRendition rendition, int targetDurationSeconds) {
        if (sourceMp4 == null || !sourceMp4.exists() || sourceMp4.length() == 0) {
            throw new VideoProcessingException("Source MP4 file is missing or empty for HLS packaging");
        }

        if (!targetOutputDir.exists() && !targetOutputDir.mkdirs()) {
            throw new VideoProcessingException("Failed to create target HLS directory: " + targetOutputDir.getAbsolutePath());
        }

        File playlistFile = new File(targetOutputDir, "index.m3u8");
        List<String> command = buildFfmpegHlsCommand(sourceMp4, playlistFile, targetDurationSeconds);

        log.info("Starting FFmpeg HLS packaging for rendition {} -> Output dir: {}",
                rendition.getResolution(), targetOutputDir.getAbsolutePath());

        ProcessExecutionResult result = processRunner.execute(
                command,
                properties.getTimeoutSeconds()
        );

        if (!result.isSuccess()) {
            log.error("FFmpeg HLS packaging failed with exit code {}. Stderr: {}", result.exitCode(), result.stderr());
            throw new VideoProcessingException("FFmpeg HLS packaging failed: " + result.stderr());
        }

        File initSegmentFile = new File(targetOutputDir, "init.mp4");
        File[] segmentFiles = targetOutputDir.listFiles((dir, name) -> name.endsWith(".m4s"));

        List<File> mediaSegments = new ArrayList<>();
        if (segmentFiles != null) {
            mediaSegments = new ArrayList<>(Arrays.asList(segmentFiles));
            mediaSegments.sort(Comparator.comparing(File::getName));
        }

        if (!playlistFile.exists() || playlistFile.length() == 0) {
            throw new VideoProcessingException("FFmpeg completed but variant playlist was not created");
        }

        if (!initSegmentFile.exists() || initSegmentFile.length() == 0) {
            throw new VideoProcessingException("FFmpeg completed but init.mp4 was not created");
        }

        if (mediaSegments.isEmpty()) {
            throw new VideoProcessingException("FFmpeg completed but no .m4s media segments were created");
        }

        long totalBitrateBps = ((long) rendition.getBitrateKbps() + (long) rendition.getAudioBitrateKbps()) * 1000L;
        long peakBandwidthBps = (long) (totalBitrateBps * 1.15); // Add 15% peak burst headroom

        String codecs = resolveHlsCodecs(rendition.getVideoCodec(), rendition.getAudioCodec(), rendition.getHeight());

        log.info("Successfully packaged HLS rendition {}: {} media segments, init size: {} bytes, playlist size: {} bytes",
                rendition.getResolution(), mediaSegments.size(), initSegmentFile.length(), playlistFile.length());

        return HlsPackagingResult.builder()
                .resolution(rendition.getResolution())
                .playlistFile(playlistFile)
                .initSegmentFile(initSegmentFile)
                .mediaSegmentFiles(Collections.unmodifiableList(mediaSegments))
                .segmentCount(mediaSegments.size())
                .targetDurationSeconds(targetDurationSeconds)
                .bandwidthBps(peakBandwidthBps)
                .averageBandwidthBps(totalBitrateBps)
                .codecs(codecs)
                .width(rendition.getWidth())
                .height(rendition.getHeight())
                .frameRate(rendition.getFrameRate())
                .build();
    }

    private List<String> buildFfmpegHlsCommand(File sourceMp4, File playlistFile, int targetDurationSeconds) {
        List<String> command = new ArrayList<>();
        command.add(properties.getFfmpegPath());
        command.add("-y");
        command.add("-i");
        command.add(sourceMp4.getAbsolutePath());

        // Stream copy to avoid re-encoding
        command.add("-c");
        command.add("copy");

        // Format HLS
        command.add("-f");
        command.add("hls");

        // Segment duration & playlist type
        command.add("-hls_time");
        command.add(String.valueOf(targetDurationSeconds));
        command.add("-hls_playlist_type");
        command.add("vod");

        // fMP4 (CMAF) configuration
        command.add("-hls_segment_type");
        command.add("fmp4");
        command.add("-hls_fmp4_init_filename");
        command.add("init.mp4");
        command.add("-hls_segment_filename");
        command.add("segment_%05d.m4s");

        // Output variant playlist path
        command.add(playlistFile.getAbsolutePath());

        return command;
    }

    private String resolveHlsCodecs(String videoCodec, String audioCodec, int height) {
        String videoAvc;
        if (height >= 1080) {
            videoAvc = "avc1.640028"; // High Profile Level 4.0
        } else if (height >= 720) {
            videoAvc = "avc1.4d401f"; // Main Profile Level 3.1
        } else if (height >= 360) {
            videoAvc = "avc1.4d401e"; // Main Profile Level 3.0
        } else {
            videoAvc = "avc1.42c00c"; // Baseline Profile Level 1.2
        }

        String audioAac = "mp4a.40.2"; // AAC-LC
        return videoAvc + "," + audioAac;
    }
}
