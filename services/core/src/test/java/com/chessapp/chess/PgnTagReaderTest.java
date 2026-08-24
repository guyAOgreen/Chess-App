package com.chessapp.chess;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgnTagReaderTest {

    private static final String GAME = """
            [Event "Club Championship"]
            [Site "London ENG"]
            [Round "3.2"]
            [White "Green, Guy"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 Nc6 1-0
            """;

    @Test
    void readsEveryTagPairIntoTheMap() {
        assertThat(PgnTagReader.tags(GAME))
                .containsEntry("Event", "Club Championship")
                .containsEntry("Site", "London ENG")
                .containsEntry("White", "Green, Guy")
                .containsEntry("Result", "1-0");
    }

    @Test
    void keepsTheRoundTagVerbatimBecauseChesslibTurnsItIntoAnInteger() {
        assertThat(PgnTagReader.tags(GAME)).containsEntry("Round", "3.2");
    }

    @Test
    void keepsTheUnknownMarkerVerbatimRatherThanTreatingItAsAbsent() {
        assertThat(PgnTagReader.tags("[Round \"?\"]\n\n1. e4 *\n"))
                .containsEntry("Round", "?");
    }

    @Test
    void unescapesQuotesAndBackslashesInValues() {
        assertThat(PgnTagReader.tags("[White \"O'Brien, \\\"Bobby\\\"\"]\n"))
                .containsEntry("White", "O'Brien, \"Bobby\"");
        assertThat(PgnTagReader.tags("[Site \"Back\\\\slash\"]\n"))
                .containsEntry("Site", "Back\\slash");
    }

    @Test
    void acceptsIrregularWhitespaceAroundATagPair() {
        assertThat(PgnTagReader.tags("   [  Event   \"Spaced\"  ]   \n"))
                .containsEntry("Event", "Spaced");
    }

    @Test
    void returnsAnEmptyMapForTextWithNoTagPairs() {
        assertThat(PgnTagReader.tags("1. e4 e5 *")).isEmpty();
    }

    @Test
    void returnsAnEmptyMapForNull() {
        assertThat(PgnTagReader.tags(null)).isEmpty();
    }

    @Test
    void takesTheFirstValueWhenATagIsRepeated() {
        assertThat(PgnTagReader.tags("[Event \"First\"]\n[Event \"Second\"]\n"))
                .containsEntry("Event", "First");
    }

    @Test
    void returnsEverythingOutsideTheTagPairSectionAsMovetext() {
        assertThat(PgnTagReader.movetext(GAME)).isEqualTo("1. e4 e5 2. Nf3 Nc6 1-0");
    }

    @Test
    void keepsLineBreaksWithinMovetextBecausePgnWrapsLongGames() {
        String wrapped = """
                [Event "?"]

                1. e4 e5
                2. Nf3 Nc6
                """;

        assertThat(PgnTagReader.movetext(wrapped)).isEqualTo("1. e4 e5\n2. Nf3 Nc6");
    }

    @Test
    void returnsEmptyMovetextForAGameWithNoMoves() {
        assertThat(PgnTagReader.movetext("[Event \"?\"]\n[Result \"*\"]\n")).isEmpty();
    }

    @Test
    void returnsEmptyMovetextForNull() {
        assertThat(PgnTagReader.movetext(null)).isEmpty();
    }
}
