package com.chessapp.game.domain;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validation and normalisation shared by {@link NewGame} and {@link Game}, so a
 * value created before persistence and the same value rehydrated afterwards
 * cannot acquire different rules.
 */
final class GameValues {

    /** The PGN marker for an unknown tag value. */
    private static final String PGN_UNKNOWN = "?";

    private static final Pattern ECO = Pattern.compile("[A-E][0-9]{2}");

    /**
     * A result token at the very end, preceded by start-of-input or any
     * whitespace. Derived from {@link GameResult} so the tokens cannot drift,
     * and deliberately the same rule as the {@code games_movetext_no_result_token}
     * constraint in the migration: PGN wraps long games across lines, so a token
     * can follow a newline or a tab just as easily as a space.
     */
    private static final Pattern TRAILING_RESULT_TOKEN = Pattern.compile(
            Arrays.stream(GameResult.values())
                    .map(GameResult::pgnToken)
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|", "(^|\\s)(", ")$")));

    private GameValues() {
    }

    /**
     * A game-time player name. Unlike an optional tag, {@code ?} is rejected
     * rather than normalised to null: the column is {@code NOT NULL}, and a game
     * with an unknown player cannot resolve to a {@code Player} at all.
     */
    static String playerName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("name is required");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (PGN_UNKNOWN.equals(trimmed)) {
            throw new IllegalArgumentException(
                    "name must not be \"?\", the PGN unknown player marker");
        }
        if (hasControlCharacter(trimmed)) {
            throw new IllegalArgumentException(
                    "name must not contain a control character; it becomes a PGN tag value");
        }
        return trimmed;
    }

    static Integer rating(Integer raw) {
        if (raw == null) {
            return null;
        }
        if (raw <= 0) {
            throw new IllegalArgumentException("rating must be positive, was: " + raw);
        }
        return raw;
    }

    /**
     * An optional PGN tag value such as {@code Event}, {@code Site} or
     * {@code Round}. Absent, blank and the unknown marker all mean the same
     * thing, so all three become null and the column carries one representation
     * of "not known" rather than three.
     */
    static String optionalTag(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || PGN_UNKNOWN.equals(trimmed)) {
            return null;
        }
        if (hasControlCharacter(trimmed)) {
            throw new IllegalArgumentException(
                    "tag value must not contain a control character; it becomes a PGN tag value");
        }
        return trimmed;
    }

    /**
     * U+2028 LINE SEPARATOR and U+2029 PARAGRAPH SEPARATOR, written numerically
     * because both characters are invisible in source.
     */
    private static final int LINE_SEPARATOR = 0x2028;
    private static final int PARAGRAPH_SEPARATOR = 0x2029;

    /**
     * A character that cannot appear in a value we emit as a PGN tag.
     *
     * <p>{@link Character#isISOControl} is not the whole rule. Neither separator
     * is an ISO control, but Java's {@code \R} treats both as line terminators —
     * so {@code PgnTagReader}, which splits a document on {@code \R}, and the many
     * PGN readers that do the same would see a tag value spread over two lines and
     * fail to read the document back. The PGN specification defines a string token
     * as printing characters between quotation marks, so a value carrying one is
     * not a legal tag value whatever the escaping.
     */
    private static boolean hasControlCharacter(String value) {
        return value.codePoints().anyMatch(character -> Character.isISOControl(character)
                || character == LINE_SEPARATOR || character == PARAGRAPH_SEPARATOR);
    }

    static String eco(String raw) {
        String tag = optionalTag(raw);
        if (tag == null) {
            return null;
        }
        if (!ECO.matcher(tag).matches()) {
            throw new IllegalArgumentException(
                    "eco must be a letter A-E followed by two digits, was: " + raw);
        }
        return tag;
    }

    static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    /**
     * Movetext holds validated SAN with move numbers and nothing else. The two
     * shapes rejected here are the ones that would turn the column into something
     * other than that: a whole PGN document pasted in, and a terminal result
     * token duplicating the authoritative {@code result} column.
     *
     * <p>Move legality is not checked — that needs the chess rules library and
     * belongs to the PGN parsing service. This guards the column's meaning, not
     * the game's correctness.
     */
    static String movetext(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("movetext is required");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("movetext must not be blank");
        }
        if (trimmed.indexOf('[') >= 0) {
            throw new IllegalArgumentException(
                    "movetext must not contain tag pairs; tags are held in their own columns");
        }
        if (TRAILING_RESULT_TOKEN.matcher(trimmed).find()) {
            throw new IllegalArgumentException(
                    "movetext must not end in a result token; the result column is authoritative,"
                            + " and assembly appends the token from it");
        }
        return trimmed;
    }
}
