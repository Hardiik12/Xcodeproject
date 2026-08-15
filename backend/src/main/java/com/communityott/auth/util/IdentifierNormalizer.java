package com.communityott.auth.util;

import com.communityott.auth.entity.AuthIdentifierType;
import com.communityott.common.exception.InvalidIdentifierException;

import java.util.regex.Pattern;

/**
 * Utility for validating and normalizing user identifiers (email addresses and phone numbers).
 */
public final class IdentifierNormalizer {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{9,14}$");

    private IdentifierNormalizer() {
        // Utility class
    }

    /**
     * Normalizes an identifier based on its specified type.
     *
     * @param type the identifier type (EMAIL or PHONE)
     * @param identifier the raw input string
     * @return normalized canonical identifier string
     * @throws InvalidIdentifierException if the identifier is null, blank, or invalid
     */
    public static String normalize(AuthIdentifierType type, String identifier) {
        if (type == null) {
            throw new InvalidIdentifierException("Identifier type cannot be null");
        }
        return switch (type) {
            case EMAIL -> normalizeEmail(identifier);
            case PHONE -> normalizePhone(identifier);
        };
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidIdentifierException("Email identifier cannot be empty");
        }

        String normalized = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidIdentifierException("Invalid email format: " + email);
        }

        return normalized;
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new InvalidIdentifierException("Phone identifier cannot be empty");
        }

        String cleaned = phone.trim().replaceAll("[\\s\\-\\(\\)]", "");
        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }

        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new InvalidIdentifierException("Invalid phone number format: " + phone);
        }

        return cleaned;
    }
}
