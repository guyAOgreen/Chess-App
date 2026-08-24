package com.chessapp.chess.chesslib;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.chess.PgnErrorCode;
import com.chessapp.chess.PgnParseResult;
import com.chessapp.game.domain.GameResult;
import com.github.bhlangonijr.chesslib.move.MoveList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * One test per constraint recorded in ADR 0001, expressed through our own API.
 *
 * <p>These are assertions about behaviour we depend on, not about behaviour we
 * implement. A library upgrade that breaks one should fail this build rather than
 * quietly change what gets stored.
 */
class ChesslibContractTest {

    private final ChesslibPgnParser parser = new ChesslibPgnParser();

    private static String game(String movetext) {
        return """
                [White "A"]
                [Black "B"]
                [Result "*"]

                """ + movetext + "\n";
    }

    private static MoveList moves(String san) {
        MoveList moves = new MoveList();
        moves.loadFromSan(san);
        return moves;
    }

    private PgnParseResult.Rejected rejected(String pgn) {
        PgnParseResult result = parser.parse(pgn);
        assertThat(result).isInstanceOf(PgnParseResult.Rejected.class);
        return (PgnParseResult.Rejected) result;
    }

    /**
     * Constraint 1, and the reason the replay loop exists. chesslib accepts this
     * input through every path it offers, so this test passes only while our own
     * legality check is in place and fails the moment someone removes it.
     */
    @Test
    void rejectsAPawnMovingThreeSquaresWhichChesslibAcceptsThroughEveryPathItOffers() {
        PgnParseResult.Rejected rejected = rejected(game("1. e5 e5 *"));

        assertThat(rejected.error().code()).isEqualTo(PgnErrorCode.ILLEGAL_MOVE);
        assertThat(rejected.error().ply()).isEqualTo(1);
    }

    /** Constraint 2: whatever unchecked type the library throws, and wherever. */
    @Test
    void turnsSanCapturingTheKingIntoARejectionRatherThanAnUncheckedException() {
        PgnParseResult.Rejected rejected = rejected(game("1. e4 e5 2. Qh5 Nc6 3. Qxe8 *"));

        assertThat(rejected.error().code())
                .isIn(PgnErrorCode.NOT_PGN, PgnErrorCode.UNREADABLE_MOVE, PgnErrorCode.ILLEGAL_MOVE);
        assertThat(rejected.error().message()).isNotBlank();
    }

    /** Constraint 3: the iterator returns a game before its moves have been read. */
    @Test
    void rejectsADocumentWhoseMovesAreInvalidRatherThanReturningTheUnverifiedGame() {
        assertThat(rejected(game("1. e4 e5 2. Nf3 Nc6 3. e6 *")).error().code())
                .isEqualTo(PgnErrorCode.ILLEGAL_MOVE);
    }

    /** Constraint 3, second half: no moves at all throws inside the library. */
    @Test
    void rejectsAGameWithNoMovesRatherThanThrowingFromInsideTheLibrary() {
        assertThat(rejected(game("*")).error().code()).isEqualTo(PgnErrorCode.NO_MOVES);
    }

    /** Constraint 4: the terminal token must not reach movetext. */
    @Test
    void keepsTheTerminalResultTokenOutOfMovetext() {
        PgnParseResult result = parser.parse("""
                [White "A"]
                [Black "B"]
                [Result "1-0"]

                1. e4 e5 2. Nf3 Nc6 1-0
                """);

        assertThat(result).isInstanceOfSatisfying(PgnParseResult.Parsed.class, parsed ->
                assertThat(parsed.game().movetext()).isEqualTo("1. e4 e5 2. Nf3 Nc6"));
    }

    /** Constraint 6: a Board per operation, never shared. */
    @Test
    void parsesConcurrentlyWithoutInterference() throws Exception {
        String pgn = """
                [White "A"]
                [Black "B"]
                [Result "1-0"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """;
        Callable<String> parse = () -> {
            PgnParseResult result = parser.parse(pgn);
            return ((PgnParseResult.Parsed) result).game().movetext();
        };

        try (ExecutorService threads = Executors.newFixedThreadPool(8)) {
            List<Future<String>> results = threads.invokeAll(java.util.Collections.nCopies(64, parse));
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
            }
        }
    }

    /**
     * The mate-winner ternary is one expression, so an inversion would flip every
     * mated game at once. Both colours and stalemate are pinned here.
     */
    @Test
    void namesTheRightWinnerForCheckmateByEitherColourAndDrawsAStalemate() {
        ValidatedMoves whiteMates = ValidatedMoves.of(moves(
                "1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7#"));
        ValidatedMoves blackMates = ValidatedMoves.of(moves("1. f3 e5 2. g4 Qh4#"));
        ValidatedMoves stalemate = ValidatedMoves.of(moves(
                "1. e3 a5 2. Qh5 Ra6 3. Qxa5 h5 4. Qxc7 Rah6 5. h4 f6 6. Qxd7+ Kf7"
                        + " 7. Qxb7 Qd3 8. Qxb8 Qh7 9. Qxc8 Kg6 10. Qe6"));

        assertThat(whiteMates.terminalResult()).isEqualTo(GameResult.WHITE_WON);
        assertThat(blackMates.terminalResult()).isEqualTo(GameResult.BLACK_WON);
        assertThat(stalemate.terminalResult()).isEqualTo(GameResult.DRAW);
    }
}
