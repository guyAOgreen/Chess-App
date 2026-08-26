package com.chessapp.game.persistence;

import java.util.Locale;

/**
 * Turns a search term into a SQL {@code LIKE} pattern matching it as a literal
 * substring.
 *
 * <p>{@code %} and {@code _} are metacharacters inside a pattern. Left alone, a
 * search for {@code _} matches every single-character value and a {@code %} in the
 * term matches anything. The term still binds as a parameter, so this is not
 * injection — the filter would simply answer a different question from the one the
 * user asked.
 *
 * <p>Escaping is a single pass over the characters rather than successive
 * {@code String.replace} calls. Replacing {@code %} and {@code _} before the
 * backslash would then escape the backslashes just inserted, turning every escape
 * into a literal backslash followed by an unescaped metacharacter. A single pass
 * cannot express that bug, so the ordering hazard is removed rather than
 * documented.
 *
 * <p>Folded with {@link Locale#ROOT} so the result does not depend on the server's
 * default locale. The column is folded separately, by SQL {@code lower()}.
 */
final class LikePattern {

    /** Must be the escape character passed to {@code CriteriaBuilder.like}. */
    static final char ESCAPE = '\\';

    private LikePattern() {
    }

    /** A pattern matching any value containing {@code term}, ignoring case. */
    static String containing(String term) {
        return "%" + escape(term.toLowerCase(Locale.ROOT)) + "%";
    }

    private static String escape(String term) {
        StringBuilder escaped = new StringBuilder(term.length());
        for (int index = 0; index < term.length(); index++) {
            char character = term.charAt(index);
            if (character == ESCAPE || character == '%' || character == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
