package com.chessapp.chess.chesslib;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.chess.ParsedGame;
import com.chessapp.chess.PgnErrorCode;
import com.chessapp.chess.PgnParseResult;
import com.chessapp.game.domain.GameResult;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChesslibPgnParserTest {

    private final ChesslibPgnParser parser = new ChesslibPgnParser();

    private ParsedGame parsed(String pgn) {
        PgnParseResult result = parser.parse(pgn);
        assertThat(result).isInstanceOf(PgnParseResult.Parsed.class);
        return ((PgnParseResult.Parsed) result).game();
    }

    private PgnErrorCode rejectedCode(String pgn) {
        PgnParseResult result = parser.parse(pgn);
        assertThat(result).isInstanceOf(PgnParseResult.Rejected.class);
        return ((PgnParseResult.Rejected) result).error().code();
    }

    private static final String COMPLETE = """
            [Event "Club Championship"]
            [Site "London ENG"]
            [Date "2026.03.14"]
            [Round "3.2"]
            [White "Green, Guy"]
            [Black "Club Opponent"]
            [Result "1-0"]
            [WhiteElo "1850"]
            [BlackElo "?"]
            [ECO "C60"]
            [TimeControl "40/7200"]

            1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
            """;

    @Nested
    class SuccessfulParse {

        @Test
        void readsEveryModelledTag() {
            ParsedGame game = parsed(COMPLETE);

            assertThat(game.event()).isEqualTo("Club Championship");
            assertThat(game.site()).isEqualTo("London ENG");
            assertThat(game.playedOn()).isEqualTo(LocalDate.of(2026, 3, 14));
            assertThat(game.whiteName()).isEqualTo("Green, Guy");
            assertThat(game.blackName()).isEqualTo("Club Opponent");
            assertThat(game.whiteRating()).isEqualTo(1850);
            assertThat(game.blackRating()).isNull();
            assertThat(game.eco()).isEqualTo("C60");
            assertThat(game.result()).isEqualTo(GameResult.WHITE_WON);
        }

        @Test
        void keepsTheRoundTagVerbatim() {
            assertThat(parsed(COMPLETE).round()).isEqualTo("3.2");
        }

        @Test
        void producesMovetextWithNoTagPairsAndNoResultToken() {
            assertThat(parsed(COMPLETE).movetext()).isEqualTo("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
        }

        @Test
        void readsAGameWhoseMovetextHasNoTerminalTokenRatherThanSilentlyDroppingItsMoves() {
            String noToken = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 2. Nf3 Nc6
                    """;

            assertThat(parsed(noToken).movetext()).isEqualTo("1. e4 e5 2. Nf3 Nc6");
        }

        @Test
        void dropsCommentsNagsAndVariations() {
            String annotated = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    1. e4 {good} e5 $1 (1... c5 2. Nf3) 2. Nf3 *
                    """;

            assertThat(parsed(annotated).movetext()).isEqualTo("1. e4 e5 2. Nf3");
        }

        @Test
        void takesTheResultFromTheTerminalTokenWhenThereIsNoResultTag() {
            String noTag = """
                    [White "A"]
                    [Black "B"]

                    1. e4 e5 0-1
                    """;

            assertThat(parsed(noTag).result()).isEqualTo(GameResult.BLACK_WON);
        }

        @Test
        void acceptsADecisiveResultInANonTerminalPositionBecauseResignationIsNotOnTheBoard() {
            String resigned = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 1-0
                    """;

            assertThat(parsed(resigned).result()).isEqualTo(GameResult.WHITE_WON);
        }
    }

    @Nested
    class Rejections {

        @Test
        void rejectsNullAndBlankInput() {
            assertThat(rejectedCode(null)).isEqualTo(PgnErrorCode.NOT_PGN);
            assertThat(rejectedCode("   ")).isEqualTo(PgnErrorCode.NOT_PGN);
        }

        @Test
        void rejectsTextThatIsNotPgnAtAll() {
            assertThat(rejectedCode("this is not a chess game")).isEqualTo(PgnErrorCode.NOT_PGN);
        }

        @Test
        void rejectsADocumentHoldingMoreThanOneGame() {
            String two = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 1-0

                    [White "C"]
                    [Black "D"]
                    [Result "0-1"]

                    1. d4 d5 0-1
                    """;

            assertThat(rejectedCode(two)).isEqualTo(PgnErrorCode.MULTIPLE_GAMES);
        }

        @Test
        void rejectsAGameStartingFromANonStandardPosition() {
            String study = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]
                    [SetUp "1"]
                    [FEN "8/8/8/8/8/5k2/6q1/7K b - - 0 1"]

                    1... Qg1+ *
                    """;

            assertThat(rejectedCode(study)).isEqualTo(PgnErrorCode.NON_STANDARD_START_POSITION);
        }

        @Test
        void rejectsAGameWithNoMoves() {
            String empty = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    *
                    """;

            assertThat(rejectedCode(empty)).isEqualTo(PgnErrorCode.NO_MOVES);
        }

        @Test
        void rejectsADocumentWithNoMovetextSectionAtAllAsHavingNoMoves() {
            String tagsOnly = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]
                    """;

            assertThat(rejectedCode(tagsOnly)).isEqualTo(PgnErrorCode.NO_MOVES);
        }

        @Test
        void rejectsMovetextThatCarriesOnlyAnnotationsAndNoActualMoves() {
            String annotationsOnly = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    {no moves here} *
                    """;

            assertThat(rejectedCode(annotationsOnly)).isEqualTo(PgnErrorCode.UNREADABLE_MOVE);
        }

        @Test
        void rejectsAnIllegalMoveAndSaysWhereItIs() {
            String illegal = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    1. e4 e5 2. Nf3 Nc6 3. e6 *
                    """;

            PgnParseResult result = parser.parse(illegal);

            assertThat(result).isInstanceOfSatisfying(PgnParseResult.Rejected.class, rejected -> {
                assertThat(rejected.error().code()).isEqualTo(PgnErrorCode.ILLEGAL_MOVE);
                assertThat(rejected.error().ply()).isEqualTo(5);
            });
        }

        @Test
        void rejectsSanThatCannotBeUnderstoodBecauseTheLibraryThrowsDuringIteration() {
            // chesslib's PgnIterator throws PgnException while iterating for this
            // input, before move loading ever runs, so this surfaces as NOT_PGN
            // rather than UNREADABLE_MOVE. Verified empirically: the exception is
            // com.github.bhlangonijr.chesslib.pgn.PgnException, thrown from
            // iterator.next(), not from Game.loadMoveText().
            String nonsense = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    1. e4 e5 2. Zz9 *
                    """;

            assertThat(rejectedCode(nonsense)).isEqualTo(PgnErrorCode.NOT_PGN);
        }

        @Test
        void rejectsAGameWhosePlayerIsUnknown() {
            String unknown = """
                    [White "?"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 1-0
                    """;

            assertThat(rejectedCode(unknown)).isEqualTo(PgnErrorCode.PLAYER_UNKNOWN);
        }

        @Test
        void rejectsAPlayerNameCarryingAControlCharacterRatherThanLettingTheDomainThrow() {
            String tabbed = """
                    [White "Green,\tGuy"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 1-0
                    """;

            assertThat(rejectedCode(tabbed)).isEqualTo(PgnErrorCode.PLAYER_UNKNOWN);
        }

        @Test
        void rejectsAGameWithNoResultAtAll() {
            String none = """
                    [White "A"]
                    [Black "B"]

                    1. e4 e5
                    """;

            assertThat(rejectedCode(none)).isEqualTo(PgnErrorCode.RESULT_MISSING);
        }

        @Test
        void stillReportsAMissingResultWhenNeitherATagNorATerminalTokenIsPresent() {
            String neither = """
                    [White "A"]
                    [Black "B"]

                    1. e4 e5
                    """;

            assertThat(rejectedCode(neither)).isEqualTo(PgnErrorCode.RESULT_MISSING);
        }

        @Test
        void rejectsAResultTagThatDisagreesWithTheTerminalToken() {
            String conflict = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 0-1
                    """;

            assertThat(rejectedCode(conflict)).isEqualTo(PgnErrorCode.RESULT_CONFLICT);
        }

        @Test
        void rejectsADeclaredResultThatContradictsCheckmateOnTheBoard() {
            String wrong = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]

                    1. f3 e5 2. g4 Qh4# 1-0
                    """;

            assertThat(rejectedCode(wrong)).isEqualTo(PgnErrorCode.RESULT_CONTRADICTS_POSITION);
        }
    }
}
