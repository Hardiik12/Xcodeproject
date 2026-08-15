package com.communityott.content.storage;

import com.communityott.common.exception.InvalidVideoFormatException;
import com.communityott.common.exception.VideoUploadSizeExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class VideoUploadValidator {

    private final StorageProperties storageProperties;

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidVideoFormatException("Video file must not be empty");
        }

        // File size validation
        long maxSizeBytes = storageProperties.getUpload().getMaxFileSizeBytes();
        if (file.getSize() > maxSizeBytes) {
            throw new VideoUploadSizeExceededException(file.getSize(), maxSizeBytes);
        }

        // Filename & extension validation
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new InvalidVideoFormatException("Video file must have a valid filename");
        }

        String lowerFilename = originalFilename.toLowerCase(Locale.ROOT);
        List<String> allowedExtensions = storageProperties.getUpload().getAllowedExtensions();
        boolean hasValidExtension = allowedExtensions.stream().anyMatch(lowerFilename::endsWith);
        if (!hasValidExtension) {
            throw new InvalidVideoFormatException(
                    String.format("Invalid file extension for '%s'. Allowed video extensions: %s",
                            originalFilename, String.join(", ", allowedExtensions)));
        }

        // Content-Type / MIME validation
        String contentType = file.getContentType();
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new InvalidVideoFormatException("Content-Type must be specified for video upload");
        }

        String normalizedContentType = contentType.toLowerCase(Locale.ROOT).trim();
        List<String> allowedContentTypes = storageProperties.getUpload().getAllowedContentTypes();
        boolean hasValidContentType = allowedContentTypes.stream().anyMatch(normalizedContentType::startsWith);
        if (!hasValidContentType) {
            throw new InvalidVideoFormatException(
                    String.format("Invalid video Content-Type '%s'. Allowed video types: %s",
                            contentType, String.join(", ", allowedContentTypes)));
        }
    }
}
