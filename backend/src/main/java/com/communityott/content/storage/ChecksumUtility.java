package com.communityott.content.storage;

import com.communityott.common.exception.VideoStorageException;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ChecksumUtility {

    private ChecksumUtility() {}

    public static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new VideoStorageException("SHA-256 algorithm not available in JVM: " + e.getMessage(), e);
        }
    }

    public static String toHexString(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public static String calculateSha256(InputStream inputStream) {
        try {
            MessageDigest digest = createSha256Digest();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHexString(digest.digest());
        } catch (Exception e) {
            throw new VideoStorageException("Failed to calculate SHA-256 checksum: " + e.getMessage(), e);
        }
    }
}
