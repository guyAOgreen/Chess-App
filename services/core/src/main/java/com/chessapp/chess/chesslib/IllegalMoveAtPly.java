package com.chessapp.chess.chesslib;

/**
 * Thrown by {@link ValidatedMoves} when a move is not legal in the position it is
 * played from. Carries the ply so the parser can report where the game stopped
 * making sense.
 */
class IllegalMoveAtPly extends RuntimeException {

    private final int ply;

    IllegalMoveAtPly(int ply, String move) {
        super("move " + moveNumber(ply) + (isWhiteMove(ply) ? ". " : "... ") + move
                + " is not legal in this position");
        this.ply = ply;
    }

    /** 1-based half-move index. Ply 1 is White's first move. */
    int ply() {
        return ply;
    }

    private static int moveNumber(int ply) {
        return (ply + 1) / 2;
    }

    private static boolean isWhiteMove(int ply) {
        return ply % 2 == 1;
    }
}
