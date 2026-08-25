package com.chessapp.chess;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/**
 * Turns raw PGN tag values into the types the rest of the application uses.
 *
 * <p>Values that cannot be used become null rather than rejecting the game.
 * A decorative tag is not worth failing an import over, and ADR 0002 already
 * accepts the resulting precision loss for dates. What a document says about the
 * moves is a different matter, and is validated rather than normalised.
 */
public final class PgnTagValues {

    /** The PGN marker for an unknown tag value. */
    private static final String UNKNOWN = "?";

    private static final Pattern ECO = Pattern.compile("[A-E][0-9]{2}");

    private static final Pattern RATING = Pattern.compile("[0-9]{1,6}");

    /** STRICT rejects 2026.02.30, which SMART would silently move to the 28th. */
    private static final DateTimeFormatter PGN_DATE =
            DateTimeFormatter.ofPattern("uuuu.MM.dd").withResolverStyle(ResolverStyle.STRICT);

    private PgnTagValues() {
    }

    /**
     * Absent, blank and {@code ?} all mean the same thing, so all three become null.
     * A value carrying a control character is unusable as a PGN tag and is treated
     * the same as absent, since {@link String#trim()} only strips control characters
     * from the ends.
     */
    public static String optional(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || UNKNOWN.equals(trimmed) || hasControlCharacter(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    /**
     * Set only when the date is fully known and real. ADR 0002 stores no precision,
     * so a partial date is null and the original tag survives in {@code source_pgn}.
     */
    public static LocalDate date(String raw) {
        String value = optional(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, PGN_DATE);
        } catch (DateTimeParseException notAFullyKnownDate) {
            return null;
        }
    }

    public static Integer rating(String raw) {
        String value = optional(raw);
        if (value == null || !RATING.matcher(value).matches()) {
            return null;
        }
        int rating = Integer.parseInt(value);
        return rating > 0 ? rating : null;
    }

    public static String eco(String raw) {
        String value = optional(raw);
        return value != null && ECO.matcher(value).matches() ? value : null;
    }
}
