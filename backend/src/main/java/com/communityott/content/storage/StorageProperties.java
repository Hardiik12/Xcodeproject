package com.communityott.content.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "communityott.storage")
@Getter
@Setter
public class StorageProperties {

    private String type = "minio";
    private Minio minio = new Minio();
    private Upload upload = new Upload();

    @Getter
    @Setter
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "communityott";
        private String secretKey = "communityott_minio_password";
        private String bucket = "communityott-media";
        private boolean autoCreateBucket = true;
    }

    @Getter
    @Setter
    public static class Upload {
        private long maxFileSizeBytes = 5368709120L; // 5 GB
        private List<String> allowedContentTypes = List.of(
                "video/mp4",
                "video/quicktime",
                "video/x-matroska",
                "video/webm"
        );
        private List<String> allowedExtensions = List.of(
                ".mp4",
                ".mov",
                ".mkv",
                ".webm"
        );
    }
}
