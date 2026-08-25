package com.chessapp.chess;

import com.chessapp.game.domain.GameResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Counts the moves a movetext section actually submits, so the parser can tell
 * whether the chess library read all of them.
 *
 * <p>This exists because chesslib can stop reading a movetext part way through and
 * report success. Two shapes were observed: a {@code ;} comment, which chesslib
 * treats as commenting out the remainder of the whole movetext rather than the
 * remainder of the line, and a token it cannot decode such as the ChessBase null
 * move {@code Z0}, after which it simply keeps what it had. Either way a real game
 * would be stored with its later moves missing and nothing to show for it.
 *
 * <p>So the stripping here follows the PGN specification rather than chesslib's
 * behaviour — that divergence is the whole point. A count that disagrees with the
 * library's is a document we could not read, and the project rejects rather than
 * repairs.
 */
public final class PgnMoveCounter {

    private static final char LINE_FEED = 0x0A;

    private static final char CARRIAGE_RETURN = 0x0D;

    private static final char FORM_FEED = 0x0C;

    private static final char VERTICAL_TAB = 0x0B;

    private static final char NEXT_LINE = 0x85;

    private static final char LINE_SEPARATOR = 0x2028;

    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    private PgnMoveCounter() {
    }

    /**
     * The number of move tokens in a movetext section: everything left once
     * comments, recursive variations, NAGs, move number indicators and the
     * terminal result token are removed.
     *
     * <p>Suffix annotations such as {@code !?} stay attached to their move, and a
     * move number written without a space ({@code 1.e4}) still counts as one move.
     */
    public static int count(String movetext) {
        if (movetext == null) {
            return 0;
        }
        List<String> moves = new ArrayList<>();
        for (String token : withoutAnnotations(movetext).trim().split("\\s+")) {
            // A move number indicator is a run of digits followed by one or more
            // periods, and need not be a token of its own: "2...Nc6" is one token
            // carrying one move. A bare run of periods is the same indicator with
            // its number omitted.
            String move = token.replaceFirst("^[0-9]*\\.+", "");
            if (!move.isEmpty()) {
                moves.add(move);
            }
        }
        if (!moves.isEmpty() && GameResult.fromPgnToken(moves.getLast()) != null) {
            moves.removeLast();
        }
        return moves.size();
    }

    /**
     * Removes brace comments, {@code ;} comments, recursive variations and NAGs.
     *
     * <p>A {@code ;} comment ends at the end of its LINE, which is what the
     * specification says and precisely where chesslib disagrees. Brace comments do
     * not nest and may span lines; variations do nest. Comments are handled at any
     * variation depth, so a {@code )} inside a comment does not close a variation.
     * Each removal leaves a space behind, so tokens either side of it cannot merge.
     */
    private static String withoutAnnotations(String movetext) {
        StringBuilder plain = new StringBuilder(movetext.length());
        int variationDepth = 0;
        for (int i = 0; i < movetext.length(); i++) {
            char current = movetext.charAt(i);
            switch (current) {
                case '{' -> {
                    i = endOf(movetext, i, '}');
                    plain.append(' ');
                }
                case ';' -> {
                    i = endOfLine(movetext, i);
                    plain.append(' ');
                }
                case '$' -> {
                    i = endOfToken(movetext, i);
                    plain.append(' ');
                }
                case '(' -> {
                    variationDepth++;
                    plain.append(' ');
                }
                case ')' -> {
                    if (variationDepth > 0) {
                        variationDepth--;
                    }
                    plain.append(' ');
                }
                default -> {
                    if (variationDepth == 0) {
                        plain.append(current);
                    }
                }
            }
        }
        return plain.toString();
    }

    /** The closing character's index, or the end of the text when it never closes. */
    private static int endOf(String movetext, int from, char closing) {
        int closed = movetext.indexOf(closing, from + 1);
        return closed < 0 ? movetext.length() : closed;
    }

    /** The last index before the line terminator, so the terminator itself survives. */
    private static int endOfLine(String movetext, int from) {
        for (int i = from + 1; i < movetext.length(); i++) {
            if (isLineTerminator(movetext.charAt(i))) {
                return i - 1;
            }
        }
        return movetext.length();
    }

    /**
     * The same line terminators {@code \R} recognises, because that is what
     * {@link PgnTagReader} splits the document on: a {@code ;} comment must end
     * where the reader agrees a line ends. Written as numeric constants rather
     * than escapes so no source-level unicode escape can turn into a real line
     * break in this file.
     */
    private static boolean isLineTerminator(char character) {
        return character == CARRIAGE_RETURN || character == LINE_FEED
                || character == FORM_FEED || character == VERTICAL_TAB
                || character == NEXT_LINE || character == LINE_SEPARATOR
                || character == PARAGRAPH_SEPARATOR;
    }

    private static int endOfToken(String movetext, int from) {
        for (int i = from + 1; i < movetext.length(); i++) {
            if (Character.isWhitespace(movetext.charAt(i))) {
                return i - 1;
            }
        }
        return movetext.length();
    }
}
