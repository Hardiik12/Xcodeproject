package com.communityott.content.processing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
@ConfigurationProperties(prefix = "communityott.video.processing")
@Getter
@Setter
public class FFmpegProperties {

    /**
     * Path to ffmpeg executable or binary name.
     */
    private String ffmpegPath = "ffmpeg";

    /**
     * Path to ffprobe executable or binary name.
     */
    private String ffprobePath = "ffprobe";

    /**
     * Execution timeout for ffmpeg/ffprobe commands in seconds.
     */
    private int timeoutSeconds = 120;

    /**
     * Maximum concurrent background processing worker threads.
     */
    private int maxConcurrentJobs = 2;

    /**
     * Scratch directory for local temporary video processing.
     */
    private String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "communityott-videos";

    /**
     * Stale heartbeat threshold in seconds for crash recovery.
     */
    private int heartbeatTimeoutSeconds = 300;
}
