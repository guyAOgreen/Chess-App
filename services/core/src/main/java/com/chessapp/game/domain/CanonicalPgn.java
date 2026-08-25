package com.chessapp.game.domain;

import java.time.format.DateTimeFormatter;

/**
 * Assembles the canonical PGN document for a {@link Game}.
 *
 * <p>ADR 0002 makes the moves canonical and the metadata relational, so the tag
 * pair section has no stored form that could go stale: it is a function of the
 * current metadata, computed on demand.
 *
 * <p>No chess library is involved. ADR 0001 records that chesslib's own
 * {@code toPgn()} injects tags that were never in the input, reorders the tag pair
 * section and mangles comment spacing.
 */
public final class CanonicalPgn {

    private static final DateTimeFormatter PGN_DATE = DateTimeFormatter.ofPattern("uuuu.MM.dd");

    /** The PGN convention for a tag value that is not known. */
    private static final String UNKNOWN = "?";

    /** The PGN convention for a date that is not known. */
    private static final String UNKNOWN_DATE = "????.??.??";

    private CanonicalPgn() {
    }

    public static String from(Game game) {
        if (game == null) {
            throw new IllegalArgumentException("game is required");
        }
        StringBuilder pgn = new StringBuilder();
        tag(pgn, "Event", game.event());
        tag(pgn, "Site", game.site());
        tag(pgn, "Date", game.playedOn() == null ? UNKNOWN_DATE : PGN_DATE.format(game.playedOn()));
        tag(pgn, "Round", game.round());
        tag(pgn, "White", game.white().name());
        tag(pgn, "Black", game.black().name());
        tag(pgn, "Result", game.result().pgnToken());
        optionalTag(pgn, "WhiteElo", game.white().rating());
        optionalTag(pgn, "BlackElo", game.black().rating());
        if (game.eco() != null) {
            tag(pgn, "ECO", game.eco());
        }
        pgn.append('\n');
        pgn.append(game.movetext()).append(' ').append(game.result().pgnToken()).append('\n');
        return pgn.toString();
    }

    private static void optionalTag(StringBuilder pgn, String name, Integer value) {
        if (value != null) {
            tag(pgn, name, String.valueOf(value));
        }
    }

    private static void tag(StringBuilder pgn, String name, String value) {
        pgn.append('[').append(name).append(" \"")
                .append(escape(value == null ? UNKNOWN : value))
                .append("\"]\n");
    }

    /**
     * Backslash first: escaping quotes first would then double-escape the
     * backslashes it introduced.
     *
     * <p>Control characters need no escape because they cannot be here — the domain
     * rejects them, since PGN string tokens are printing characters and no escape
     * makes an embedded newline legal.
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
