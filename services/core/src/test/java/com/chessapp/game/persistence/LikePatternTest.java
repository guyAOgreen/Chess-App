package com.chessapp.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternTest {

    @Test
    void wrapsAnOrdinaryTermInWildcardsSoItMatchesASubstring() {
        assertThat(LikePattern.containing("championship")).isEqualTo("%championship%");
    }

    @Test
    void foldsCaseSoTheComparisonIsInsensitive() {
        assertThat(LikePattern.containing("Championship")).isEqualTo("%championship%");
    }

    /**
     * Unescaped, "50%" would match anything containing "50" — including "500 Club".
     * The value still binds, so this is not injection; the filter would simply
     * answer a different question from the one asked.
     */
    @Test
    void escapesPerCentSoItMatchesLiterally() {
        assertThat(LikePattern.containing("50%")).isEqualTo("%50\\%%");
    }

    /** Unescaped, "_" is a single-character wildcard and matches every one-character value. */
    @Test
    void escapesUnderscoreSoItMatchesLiterally() {
        assertThat(LikePattern.containing("a_b")).isEqualTo("%a\\_b%");
    }

    @Test
    void escapesTheEscapeCharacterItself() {
        assertThat(LikePattern.containing("c:\\path")).isEqualTo("%c:\\\\path%");
    }

    /**
     * A backslash followed by a per cent is two literal characters in the input and
     * must become two escapes. Escaping with successive replacements in the wrong
     * order produces a single escape here, which is the classic way this is got
     * wrong; a single pass over the characters cannot express the bug.
     */
    @Test
    void escapesEachCharacterOfAnAlreadyEscapedLookingSequence() {
        assertThat(LikePattern.containing("\\%")).isEqualTo("%\\\\\\%%");
    }

    @Test
    void producesAMatchEverythingPatternForAnEmptyTerm() {
        assertThat(LikePattern.containing("")).isEqualTo("%%");
    }
}
