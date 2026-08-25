package com.chessapp.chess.chesslib;

import com.chessapp.game.domain.GameResult;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;

/**
 * Moves that have been checked for legality, with whatever the final position
 * says about the result.
 *
 * <p>Every move is replayed against {@link Board#legalMoves()}. This is not
 * belt-and-braces: no chesslib path rejects an illegal pawn move.
 * {@code MoveList.loadFromSan("1. e5 e5")} is accepted and yields
 * {@code 1. e5 exe5}, a white pawn moving three squares from e2, and the PGN
 * reader accepts the same input. ADR 0001 originally named SAN parsed through
 * {@code MoveList} as an authoritative path; it was corrected when this was found.
 *
 * <p>Deleting the replay loop as redundant would silently store illegal games.
 * {@code ChesslibContractTest} exists to fail if anyone does.
 *
 * @param movetext       normalised SAN with move numbers, trimmed
 * @param terminalResult the result the final position forces, or null when the
 *                       position is neither checkmate nor stalemate
 */
record ValidatedMoves(String movetext, GameResult terminalResult) {

    static ValidatedMoves of(MoveList moves) {
        Board board = new Board();
        int ply = 0;
        for (Move move : moves) {
            ply++;
            if (!board.legalMoves().contains(move)) {
                throw new IllegalMoveAtPly(ply, sanAt(moves, ply, move));
            }
            board.doMove(move);
        }
        return new ValidatedMoves(moves.toSanWithMoveNumbers().trim(), terminalResult(board));
    }

    /**
     * The side to move is the side that has been mated, so the other side won.
     */
    private static GameResult terminalResult(Board board) {
        if (board.isMated()) {
            return board.getSideToMove() == Side.WHITE ? GameResult.BLACK_WON : GameResult.WHITE_WON;
        }
        return board.isStaleMate() ? GameResult.DRAW : null;
    }

    /**
     * SAN for the error message, falling back to the move's own coordinate form.
     * Rendering SAN for a list containing an illegal move does work, but it is
     * formatting an input the library already mis-decoded, so it is not trusted.
     */
    private static String sanAt(MoveList moves, int ply, Move move) {
        try {
            String[] san = moves.toSanArray();
            return ply <= san.length ? san[ply - 1] : move.toString();
        } catch (RuntimeException notRenderable) {
            return move.toString();
        }
    }
}
