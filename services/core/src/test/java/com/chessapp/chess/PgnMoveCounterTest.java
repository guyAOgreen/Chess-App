package com.chessapp.chess;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgnMoveCounterTest {

    @Test
    void countsThePlainMovesOfAGame() {
        assertThat(PgnMoveCounter.count("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6")).isEqualTo(6);
    }

    @Test
    void ignoresTheTerminalResultToken() {
        assertThat(PgnMoveCounter.count("1. e4 e5 1-0")).isEqualTo(2);
        assertThat(PgnMoveCounter.count("1. e4 e5 0-1")).isEqualTo(2);
        assertThat(PgnMoveCounter.count("1. e4 e5 1/2-1/2")).isEqualTo(2);
        assertThat(PgnMoveCounter.count("1. e4 e5 *")).isEqualTo(2);
    }

    @Test
    void ignoresCommentsNagsAndVariations() {
        assertThat(PgnMoveCounter.count("1. e4 {good} e5 $1 (1... c5 2. Nf3) 2. Nf3"))
                .isEqualTo(3);
    }

    @Test
    void countsMovesSplitAcrossLinesAsOneSequence() {
        assertThat(PgnMoveCounter.count("1. e4 e5\n2. Nf3 Nc6")).isEqualTo(4);
    }

    @Test
    void countsNoMovesInMovetextThatIsOnlyAComment() {
        assertThat(PgnMoveCounter.count("{no moves here} *")).isZero();
        assertThat(PgnMoveCounter.count("$1 *")).isZero();
        assertThat(PgnMoveCounter.count("1. *")).isZero();
    }

    /**
     * The specification ends a {@code ;} comment at the end of its line. chesslib
     * ends it at the end of the whole movetext, which is exactly the divergence the
     * parser's completeness check exists to catch, so this counter must follow the
     * specification rather than the library.
     */
    @Test
    void endsASemicolonCommentAtTheEndOfItsLineNotTheEndOfTheMovetext() {
        assertThat(PgnMoveCounter.count("1. e4 e5 2. Nf3 ; developing\n2... Nc6 3. Bb5 a6 1-0"))
                .isEqualTo(6);
    }

    @Test
    void countsMovesAfterABraceCommentThatSpansLines() {
        assertThat(PgnMoveCounter.count("1. e4 {a long\nnote} e5 2. Nf3"))
                .isEqualTo(3);
    }

    @Test
    void countsNestedVariationsAsNoMovesAtAll() {
        assertThat(PgnMoveCounter.count("1. e4 (1. d4 d5 (1... Nf6 2. c4) 2. c4) e5"))
                .isEqualTo(2);
    }

    @Test
    void doesNotLetACommentInsideAVariationCloseIt() {
        assertThat(PgnMoveCounter.count("1. e4 (1. d4 {a smiley )} d5) e5")).isEqualTo(2);
    }

    @Test
    void endsASemicolonCommentInsideAVariationAtItsLineBreak() {
        assertThat(PgnMoveCounter.count("1. e4 (1. d4 ; note\nd5) e5 2. Nf3")).isEqualTo(3);
    }

    @Test
    void countsAMoveWrittenWithNoSpaceAfterItsMoveNumber() {
        assertThat(PgnMoveCounter.count("1.e4 e5 2.Nf3 Nc6")).isEqualTo(4);
    }

    @Test
    void countsABlackContinuationIndicatorAsNoMove() {
        assertThat(PgnMoveCounter.count("1. e4 e5 2. Nf3 ... Nc6")).isEqualTo(4);
    }

    @Test
    void keepsASuffixAnnotationAttachedToItsMove() {
        assertThat(PgnMoveCounter.count("1. e4! e5?! 2. Nf3")).isEqualTo(3);
    }

    @Test
    void countsATokenChesslibCannotDecodeBecauseTheDocumentStillSubmittedIt() {
        assertThat(PgnMoveCounter.count("1. e4 e5 2. Z0 1-0")).isEqualTo(3);
    }

    @Test
    void countsNothingForNullOrBlankMovetext() {
        assertThat(PgnMoveCounter.count(null)).isZero();
        assertThat(PgnMoveCounter.count("")).isZero();
        assertThat(PgnMoveCounter.count("   ")).isZero();
    }

    @Test
    void countsWhatItCanWhenAnAnnotationIsNeverClosed() {
        assertThat(PgnMoveCounter.count("1. e4 e5 {unterminated")).isEqualTo(2);
        assertThat(PgnMoveCounter.count("1. e4 e5 (unterminated")).isEqualTo(2);
    }
}
