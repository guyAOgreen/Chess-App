package com.chessapp.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalPgnTest {

    private static final UUID ID = UUID.fromString("019535d9-5b22-7f04-8e15-3c9a7d2f6b81");
    private static final UUID WHITE_ID = UUID.fromString("019535d9-3df7-79fb-b466-fa907fa17f9e");
    private static final UUID BLACK_ID = UUID.fromString("019535d9-4aa1-7c2e-9d31-2b6f1c4e8a70");

    private static Game fullyPopulated() {
        return new Game(ID,
                new GameSide(WHITE_ID, "Green, Guy", 1850),
                new GameSide(BLACK_ID, "Club Opponent", 1720),
                "Club Championship", "London ENG", "3.2", LocalDate.of(2026, 3, 14),
                GameResult.WHITE_WON, "C60", GameSource.PGN_IMPORT,
                "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6", null);
    }

    private static Game bare() {
        return new Game(ID,
                new GameSide(WHITE_ID, "Green, Guy", null),
                new GameSide(BLACK_ID, "Club Opponent", null),
                null, null, null, null, GameResult.DRAW, null, GameSource.PERSONAL,
                "1. d4 d5", null);
    }

    @Test
    void emitsTheSevenTagRosterInSpecificationOrderThenTheOptionalTags() {
        assertThat(CanonicalPgn.from(fullyPopulated())).isEqualTo("""
                [Event "Club Championship"]
                [Site "London ENG"]
                [Date "2026.03.14"]
                [Round "3.2"]
                [White "Green, Guy"]
                [Black "Club Opponent"]
                [Result "1-0"]
                [WhiteElo "1850"]
                [BlackElo "1720"]
                [ECO "C60"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """);
    }

    @Test
    void writesUnknownStringsAsQuestionMarksAndAnUnknownDateAsTheMaskedForm() {
        assertThat(CanonicalPgn.from(bare())).isEqualTo("""
                [Event "?"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "Green, Guy"]
                [Black "Club Opponent"]
                [Result "1/2-1/2"]

                1. d4 d5 1/2-1/2
                """);
    }

    @Test
    void escapesQuotesAndBackslashesInTagValues() {
        Game game = new Game(ID,
                new GameSide(WHITE_ID, "O'Brien, \"Bobby\"", null),
                new GameSide(BLACK_ID, "Back\\slash", null),
                null, null, null, null, GameResult.BLACK_WON, null, GameSource.PERSONAL,
                "1. e4 e5", null);

        assertThat(CanonicalPgn.from(game))
                .contains("[White \"O'Brien, \\\"Bobby\\\"\"]")
                .contains("[Black \"Back\\\\slash\"]");
    }

    @Test
    void usesLineFeedsAndEndsWithExactlyOneNewlineOnEveryPlatform() {
        String pgn = CanonicalPgn.from(bare());

        assertThat(pgn).doesNotContain("\r");
        assertThat(pgn).endsWith("1. d4 d5 1/2-1/2\n");
        assertThat(pgn).doesNotEndWith("\n\n");
    }

    @Test
    void appendsTheResultTokenForEveryResult() {
        for (GameResult result : GameResult.values()) {
            Game game = new Game(ID,
                    new GameSide(WHITE_ID, "A", null), new GameSide(BLACK_ID, "B", null),
                    null, null, null, null, result, null, GameSource.PERSONAL, "1. e4 e5", null);

            assertThat(CanonicalPgn.from(game))
                    .as("result %s", result)
                    .contains("[Result \"" + result.pgnToken() + "\"]")
                    .endsWith("1. e4 e5 " + result.pgnToken() + "\n");
        }
    }

    @Test
    void omitsRatingsAndEcoWhenTheyAreNotKnown() {
        assertThat(CanonicalPgn.from(bare()))
                .doesNotContain("WhiteElo")
                .doesNotContain("BlackElo")
                .doesNotContain("ECO");
    }

    @Test
    void injectsNoneOfTheTagsChesslibWouldAdd() {
        assertThat(CanonicalPgn.from(fullyPopulated()))
                .doesNotContain("PlyCount")
                .doesNotContain("TimeControl")
                .doesNotContain("Annotator");
    }
}
