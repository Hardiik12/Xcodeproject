package com.communityott.content.storage;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStorageService {

    String uploadObject(String bucket, String key, InputStream inputStream, long size, String contentType);

    InputStream getObject(String bucket, String key);

    boolean doesObjectExist(String bucket, String key);

    void deleteObject(String bucket, String key);

    String generatePresignedGetUrl(String bucket, String key, Duration duration);

    String getBucketName();
}
