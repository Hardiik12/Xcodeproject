package com.communityott.content.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultFFprobeService implements FFprobeService {

    private final FFmpegProperties properties;
    private final ProcessRunner processRunner;
    private final ObjectMapper objectMapper;

    @Override
    public MediaProbeResult probe(File mediaFile) {
        if (mediaFile == null || !mediaFile.exists() || !mediaFile.isFile()) {
            return MediaProbeResult.builder()
                    .validMedia(false)
                    .validationError("Media file does not exist or is not a valid file")
                    .build();
        }

        List<String> commandArgs = new ArrayList<>();
        commandArgs.add(properties.getFfprobePath());
        commandArgs.add("-v");
        commandArgs.add("quiet");
        commandArgs.add("-print_format");
        commandArgs.add("json");
        commandArgs.add("-show_format");
        commandArgs.add("-show_streams");
        commandArgs.add(mediaFile.getAbsolutePath());

        log.debug("Executing ffprobe probe on file: {}", mediaFile.getName());
        ProcessExecutionResult result = processRunner.execute(commandArgs, properties.getTimeoutSeconds());

        if (result.timedOut()) {
            log.error("FFprobe timed out after {}s for file: {}", properties.getTimeoutSeconds(), mediaFile.getName());
            return MediaProbeResult.builder()
                    .validMedia(false)
                    .validationError("FFprobe process timed out after " + properties.getTimeoutSeconds() + " seconds")
                    .build();
        }

        if (result.exitCode() != 0 || result.stdout() == null || result.stdout().isBlank()) {
            log.error("FFprobe failed with exit code {}: {}", result.exitCode(), result.stderr());
            return MediaProbeResult.builder()
                    .validMedia(false)
                    .validationError("FFprobe execution failed: " + (result.stderr() != null && !result.stderr().isBlank() ? result.stderr() : "Exit code " + result.exitCode()))
                    .build();
        }

        return parseProbeJson(result.stdout());
    }

    public MediaProbeResult parseProbeJson(String jsonString) {
        try {
            JsonNode root = objectMapper.readTree(jsonString);

            JsonNode formatNode = root.path("format");
            JsonNode streamsNode = root.path("streams");

            if (!streamsNode.isArray() || streamsNode.isEmpty()) {
                return MediaProbeResult.builder()
                        .validMedia(false)
                        .validationError("Media contains no audio/video streams")
                        .rawJson(jsonString)
                        .build();
            }

            Integer durationSeconds = null;
            if (formatNode.hasNonNull("duration")) {
                try {
                    double durationDbl = Double.parseDouble(formatNode.path("duration").asText());
                    durationSeconds = (int) Math.round(durationDbl);
                } catch (NumberFormatException ignored) {}
            }

            Integer bitrateKbps = null;
            if (formatNode.hasNonNull("bit_rate")) {
                try {
                    long bitRate = Long.parseLong(formatNode.path("bit_rate").asText());
                    bitrateKbps = (int) (bitRate / 1000);
                } catch (NumberFormatException ignored) {}
            }

            String containerFormat = formatNode.path("format_name").asText(null);

            Integer width = null;
            Integer height = null;
            String videoCodec = null;
            String audioCodec = null;
            String frameRate = null;

            for (JsonNode stream : streamsNode) {
                String codecType = stream.path("codec_type").asText("");
                if ("video".equalsIgnoreCase(codecType) && videoCodec == null) {
                    videoCodec = stream.path("codec_name").asText(null);
                    if (stream.hasNonNull("width")) {
                        width = stream.path("width").asInt();
                    }
                    if (stream.hasNonNull("height")) {
                        height = stream.path("height").asInt();
                    }
                    if (stream.hasNonNull("r_frame_rate")) {
                        frameRate = stream.path("r_frame_rate").asText(null);
                    }
                    if (durationSeconds == null && stream.hasNonNull("duration")) {
                        try {
                            double dur = Double.parseDouble(stream.path("duration").asText());
                            durationSeconds = (int) Math.round(dur);
                        } catch (NumberFormatException ignored) {}
                    }
                } else if ("audio".equalsIgnoreCase(codecType) && audioCodec == null) {
                    audioCodec = stream.path("codec_name").asText(null);
                }
            }

            // Validate essential media criteria
            if (videoCodec == null) {
                return MediaProbeResult.builder()
                        .validMedia(false)
                        .validationError("Media contains no video stream")
                        .rawJson(jsonString)
                        .build();
            }

            if (width == null || width <= 0 || height == null || height <= 0) {
                return MediaProbeResult.builder()
                        .validMedia(false)
                        .validationError("Media contains invalid or zero dimensions (" + width + "x" + height + ")")
                        .rawJson(jsonString)
                        .build();
            }

            return MediaProbeResult.builder()
                    .durationSeconds(durationSeconds != null ? durationSeconds : 0)
                    .width(width)
                    .height(height)
                    .bitrateKbps(bitrateKbps)
                    .containerFormat(containerFormat)
                    .videoCodec(videoCodec)
                    .audioCodec(audioCodec)
                    .frameRate(frameRate)
                    .rawJson(jsonString)
                    .validMedia(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse ffprobe json output: {}", e.getMessage(), e);
            return MediaProbeResult.builder()
                    .validMedia(false)
                    .validationError("Failed to parse probe JSON: " + e.getMessage())
                    .rawJson(jsonString)
                    .build();
        }
    }
}
