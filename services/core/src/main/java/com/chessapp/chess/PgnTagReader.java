package com.chessapp.chess;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the tag pair section of a PGN document.
 *
 * <p>This exists because chesslib's parsed model is lossy for our metadata.
 * {@code [Round "3.2"]} and {@code [Round "?"]} both arrive as the integer 1, and
 * {@code Round} appears in no property map, while ADR 0002 typed the column
 * {@code TEXT} precisely because those values are legal. A missing {@code Result}
 * is also indistinguishable from {@code *} in that model.
 *
 * <p>No chesslib import: this class reads text, and owning it makes metadata
 * fidelity our responsibility rather than a property of the library we chose.
 */
public final class PgnTagReader {

    private static final Pattern TAG_PAIR = Pattern.compile(
            "^\\s*\\[\\s*([A-Za-z0-9_]+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"\\s*]\\s*$");

    private PgnTagReader() {
    }

    /**
     * Every tag pair in the document, in the order they appear. A repeated tag keeps
     * its first value, so the first game's tags win in a document holding several —
     * which the parser rejects anyway, having counted them first.
     */
    public static Map<String, String> tags(String pgn) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (pgn == null) {
            return tags;
        }
        for (String line : pgn.split("\\R", -1)) {
            Matcher tagPair = TAG_PAIR.matcher(line);
            if (tagPair.matches()) {
                tags.putIfAbsent(tagPair.group(1), unescape(tagPair.group(2)));
            }
        }
        return tags;
    }

    /**
     * Everything that is not a tag pair, trimmed. Interior line breaks are kept:
     * PGN wraps long games across lines, and the moves either side of the break are
     * a single sequence.
     */
    public static String movetext(String pgn) {
        if (pgn == null) {
            return "";
        }
        StringBuilder movetext = new StringBuilder();
        for (String line : pgn.split("\\R", -1)) {
            if (!TAG_PAIR.matcher(line).matches()) {
                movetext.append(line).append('\n');
            }
        }
        return movetext.toString().trim();
    }

    /**
     * One pass, so an escaped backslash cannot have its output re-read as the start
     * of another escape.
     */
    private static String unescape(String value) {
        StringBuilder unescaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' && i + 1 < value.length()) {
                unescaped.append(value.charAt(++i));
            } else {
                unescaped.append(current);
            }
        }
        return unescaped.toString();
    }
}
