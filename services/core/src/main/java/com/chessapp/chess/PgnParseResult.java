package com.chessapp.chess;

/**
 * Sealed, so a caller cannot forget that parsing can fail. An invalid document is
 * an expected outcome for an import fed by users, not an exceptional condition.
 */
public sealed interface PgnParseResult {

    record Parsed(ParsedGame game) implements PgnParseResult {
    }

    record Rejected(PgnError error) implements PgnParseResult {
    }
}
