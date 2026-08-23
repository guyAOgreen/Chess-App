package com.chessapp.player.domain;

import java.util.regex.Pattern;

/**
 * Validation and normalisation shared by {@link NewPlayer} and {@link Player},
 * so a value created before persistence and the same value rehydrated afterwards
 * cannot acquire different rules.
 */
final class PlayerValues {

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern FEDERATION = Pattern.compile("[A-Z]{3}");

    /** The PGN marker for an unknown tag value. Never a real player. */
    private static final String PGN_UNKNOWN = "?";

    private PlayerValues() {
    }

    static String displayName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("displayName is required");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (PGN_UNKNOWN.equals(trimmed)) {
            throw new IllegalArgumentException(
                    "displayName must not be \"?\", the PGN unknown player marker");
        }
        return trimmed;
    }

    static String fideId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!DIGITS.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("fideId must contain digits only, was: " + raw);
        }
        return trimmed;
    }

    static String federation(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!FEDERATION.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "federation must be three uppercase letters, was: " + raw);
        }
        return trimmed;
    }
}
