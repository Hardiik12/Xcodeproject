package com.communityott.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cryptographic utility for secure OTP generation, HMAC-SHA256 hashing,
 * SHA-256 identifier key hashing, and constant-time byte comparisons.
 */
public final class OtpCryptoUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private OtpCryptoUtils() {
        // Utility class
    }

    /**
     * Generates a 64-byte (512-bit) cryptographically secure random refresh token string encoded in URL-safe Base64 without padding.
     *
     * @return cryptographically random refresh token string
     */
    public static String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        SECURE_RANDOM.nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generates an independently unpredictable numeric OTP string of the given length.
     *
     * @param length the number of digits (e.g. 6)
     * @return zero-padded numeric string (e.g. "042819")
     */
    public static String generateSecureOtp(int length) {
        int maxValue = (int) Math.pow(10, length);
        int randomValue = SECURE_RANDOM.nextInt(maxValue);
        return String.format("%0" + length + "d", randomValue);
    }

    /**
     * Computes an HMAC-SHA256 hex string representation of the submitted OTP code.
     *
     * @param otp the plaintext OTP string
     * @param secret the server-side HMAC secret
     * @return hex encoded HMAC string
     */
    public static String hashOtp(String otp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(otp.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 hash for OTP", e);
        }
    }

    /**
     * Computes a SHA-256 hex hash of a normalized user identifier for safe use in Redis keys.
     *
     * @param identifier the normalized identifier string (email or phone)
     * @return hex encoded SHA-256 hash string
     */
    public static String hashIdentifier(String identifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] rawHash = digest.digest(identifier.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Performs a constant-time comparison of two strings to prevent timing side-channel attacks.
     *
     * @param a first string
     * @param b second string
     * @return {@code true} if strings are identical; {@code false} otherwise
     */
    public static boolean constantTimeCompare(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
