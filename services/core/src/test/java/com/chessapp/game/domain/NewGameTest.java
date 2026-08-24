package com.chessapp.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NewGameTest {

    private static final GameSide WHITE =
            new GameSide(UUID.fromString("019535d9-3df7-79fb-b466-fa907fa17f9e"), "Green, Guy", 1850);
    private static final GameSide BLACK =
            new GameSide(UUID.fromString("019535d9-4aa1-7c2e-9d31-2b6f1c4e8a70"), "Club Opponent", null);
    private static final String MOVETEXT = "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6";

    /** A valid game, so each test can vary the one field it is about. */
    private static NewGame game() {
        return new NewGame(WHITE, BLACK, "Club Championship", "London ENG", "3",
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "C60", GameSource.PGN_IMPORT,
                MOVETEXT, null);
    }

    private static NewGame withEvent(String event) {
        NewGame g = game();
        return new NewGame(g.white(), g.black(), event, g.site(), g.round(), g.playedOn(),
                g.result(), g.eco(), g.source(), g.movetext(), g.sourcePgn());
    }

    private static NewGame withEco(String eco) {
        NewGame g = game();
        return new NewGame(g.white(), g.black(), g.event(), g.site(), g.round(), g.playedOn(),
                g.result(), eco, g.source(), g.movetext(), g.sourcePgn());
    }

    private static NewGame withMovetext(String movetext) {
        NewGame g = game();
        return new NewGame(g.white(), g.black(), g.event(), g.site(), g.round(), g.playedOn(),
                g.result(), g.eco(), g.source(), movetext, g.sourcePgn());
    }

    @Nested
    class RequiredValues {

        @Test
        void rejectsAMissingWhiteSide() {
            NewGame g = game();
            assertThatThrownBy(() -> new NewGame(null, g.black(), null, null, null, null,
                    g.result(), null, g.source(), g.movetext(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("white");
        }

        @Test
        void rejectsAMissingBlackSide() {
            NewGame g = game();
            assertThatThrownBy(() -> new NewGame(g.white(), null, null, null, null, null,
                    g.result(), null, g.source(), g.movetext(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("black");
        }

        @Test
        void rejectsAMissingResult() {
            NewGame g = game();
            assertThatThrownBy(() -> new NewGame(g.white(), g.black(), null, null, null, null,
                    null, null, g.source(), g.movetext(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("result");
        }

        @Test
        void rejectsAMissingSource() {
            NewGame g = game();
            assertThatThrownBy(() -> new NewGame(g.white(), g.black(), null, null, null, null,
                    g.result(), null, null, g.movetext(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("source");
        }
    }

    @Nested
    class OptionalTags {

        @Test
        void trimsAnEventThatIsPresent() {
            assertThat(withEvent("  Club Championship  ").event()).isEqualTo("Club Championship");
        }

        @Test
        void normalisesThePgnUnknownMarkerToNull() {
            assertThat(withEvent("?").event()).isNull();
        }

        @Test
        void normalisesABlankTagToNull() {
            assertThat(withEvent("   ").event()).isNull();
        }

        @Test
        void acceptsAGameWithNoOptionalMetadataAtAll() {
            NewGame g = game();
            NewGame bare = new NewGame(g.white(), g.black(), null, null, null, null,
                    GameResult.DRAW, null, GameSource.PERSONAL, MOVETEXT, null);

            assertThat(bare.event()).isNull();
            assertThat(bare.site()).isNull();
            assertThat(bare.round()).isNull();
            assertThat(bare.playedOn()).isNull();
            assertThat(bare.eco()).isNull();
            assertThat(bare.sourcePgn()).isNull();
        }
    }

    @Nested
    class Eco {

        @Test
        void acceptsAWellFormedEcoCode() {
            assertThat(withEco("C60").eco()).isEqualTo("C60");
        }

        @Test
        void rejectsAnEcoCodeOutsideTheAToERange() {
            assertThatThrownBy(() -> withEco("F60"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eco");
        }

        @Test
        void rejectsAnEcoCodeWithTheWrongNumberOfDigits() {
            assertThatThrownBy(() -> withEco("C6"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eco");
        }
    }

    @Nested
    class Movetext {

        @Test
        void trimsTheMovetext() {
            assertThat(withMovetext("  1. e4 e5  ").movetext()).isEqualTo("1. e4 e5");
        }

        @Test
        void rejectsMissingMovetext() {
            assertThatThrownBy(() -> withMovetext(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("movetext");
        }

        @Test
        void rejectsBlankMovetext() {
            assertThatThrownBy(() -> withMovetext("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void rejectsMovetextContainingTagPairs() {
            assertThatThrownBy(() -> withMovetext("[Event \"Club Championship\"]\n\n1. e4 e5"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tag pair");
        }

        @Test
        void rejectsMovetextEndingInADecisiveResultToken() {
            assertThatThrownBy(() -> withMovetext("1. e4 e5 2. Nf3 1-0"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("result token");
        }

        @Test
        void rejectsMovetextEndingInADrawToken() {
            assertThatThrownBy(() -> withMovetext("1. e4 e5 1/2-1/2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("result token");
        }

        @Test
        void rejectsMovetextEndingInTheUnfinishedToken() {
            assertThatThrownBy(() -> withMovetext("1. e4 e5 *"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("result token");
        }

        @Test
        void acceptsAMoveThatMerelyContainsADashOrStar() {
            assertThat(withMovetext("1. e4 e5 2. O-O-O Qxf7*").movetext())
                    .isEqualTo("1. e4 e5 2. O-O-O Qxf7*");
        }
    }

    @Nested
    class SourcePgn {

        @Test
        void keepsTheSubmittedDocumentVerbatimBecauseItIsProvenance() {
            NewGame g = game();
            String submitted = "[Event \"Club Championship\"]\n\n1. e4 e5 1-0\n";
            NewGame withPgn = new NewGame(g.white(), g.black(), g.event(), g.site(), g.round(),
                    g.playedOn(), g.result(), g.eco(), g.source(), g.movetext(), submitted);

            assertThat(withPgn.sourcePgn()).isEqualTo(submitted);
        }
    }
}
