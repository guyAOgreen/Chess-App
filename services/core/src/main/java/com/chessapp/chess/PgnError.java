package com.chessapp.chess;

/**
 * @param ply 1-based half-move index where the problem is, or null when the
 *            problem is not about a specific move. Ply 1 is White's first move.
 */
public record PgnError(PgnErrorCode code, String message, Integer ply) {

    public PgnError(PgnErrorCode code, String message) {
        this(code, message, null);
    }
}
