package com.chessapp.chess.chesslib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chessapp.game.domain.GameResult;
import com.github.bhlangonijr.chesslib.move.MoveList;
import org.junit.jupiter.api.Test;

class ValidatedMovesTest {

    private static MoveList moves(String san) {
        MoveList moves = new MoveList();
        moves.loadFromSan(san);
        return moves;
    }

    @Test
    void returnsNormalisedMovetextWithNoTrailingSpace() {
        ValidatedMoves validated = ValidatedMoves.of(moves("1. e4 e5 2. Nf3 Nc6"));

        assertThat(validated.movetext()).isEqualTo("1. e4 e5 2. Nf3 Nc6");
    }

    @Test
    void rejectsAPawnMovingThreeSquaresWhichChesslibItselfAccepts() {
        MoveList accepted = moves("1. e5 e5");

        assertThat(accepted.toSanWithMoveNumbers().trim())
                .as("chesslib accepts this, which is why the replay exists")
                .isEqualTo("1. e5 exe5");
        assertThatThrownBy(() -> ValidatedMoves.of(accepted))
                .isInstanceOf(IllegalMoveAtPly.class)
                .hasMessageContaining("not legal");
    }

    @Test
    void reportsThePlyAndMoveNumberOfTheFirstIllegalMove() {
        assertThatThrownBy(() -> ValidatedMoves.of(moves("1. e4 e5 2. Nf3 Nc6 3. e6")))
                .isInstanceOfSatisfying(IllegalMoveAtPly.class,
                        illegal -> assertThat(illegal.ply()).isEqualTo(5))
                .hasMessageContaining("3.");
    }

    @Test
    void reportsCheckmateAsAWinForTheSideThatDeliveredIt() {
        ValidatedMoves validated = ValidatedMoves.of(moves("1. f3 e5 2. g4 Qh4#"));

        assertThat(validated.terminalResult()).isEqualTo(GameResult.BLACK_WON);
    }

    @Test
    void reportsNoTerminalResultForAnOrdinaryPosition() {
        assertThat(ValidatedMoves.of(moves("1. e4 e5")).terminalResult()).isNull();
    }

    @Test
    void keepsTheCheckAndMateSuffixesChesslibEmits() {
        assertThat(ValidatedMoves.of(moves("1. f3 e5 2. g4 Qh4#")).movetext())
                .endsWith("Qh4#");
    }
}
