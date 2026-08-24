package com.chessapp.chess;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.chess.chesslib.ChesslibPgnParser;
import com.chessapp.game.domain.CanonicalPgn;
import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Parse, build a Game, assemble, parse again. This is what makes "a game has
 * exactly one canonical PGN" true in practice rather than by assertion: assembly
 * is deterministic, and what it emits is what the parser reads back.
 */
class PgnRoundTripTest {

    private static final UUID ID = UUID.fromString("019535d9-5b22-7f04-8e15-3c9a7d2f6b81");
    private static final UUID WHITE_ID = UUID.fromString("019535d9-3df7-79fb-b466-fa907fa17f9e");
    private static final UUID BLACK_ID = UUID.fromString("019535d9-4aa1-7c2e-9d31-2b6f1c4e8a70");

    private final PgnParser parser = new ChesslibPgnParser();

    private ParsedGame parse(String pgn) {
        PgnParseResult result = parser.parse(pgn);
        assertThat(result).as("parsing %s", pgn).isInstanceOf(PgnParseResult.Parsed.class);
        return ((PgnParseResult.Parsed) result).game();
    }

    private static Game gameFrom(ParsedGame parsed) {
        return new Game(ID,
                new GameSide(WHITE_ID, parsed.whiteName(), parsed.whiteRating()),
                new GameSide(BLACK_ID, parsed.blackName(), parsed.blackRating()),
                parsed.event(), parsed.site(), parsed.round(), parsed.playedOn(),
                parsed.result(), parsed.eco(), GameSource.PGN_IMPORT, parsed.movetext(), null);
    }

    @Test
    void assemblingWhatWasParsedProducesADocumentThatParsesBackTheSame() {
        String original = """
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
                """;

        ParsedGame first = parse(original);
        String assembled = CanonicalPgn.from(gameFrom(first));
        ParsedGame second = parse(assembled);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void assemblyIsStableSoASecondPassChangesNothing() {
        String original = """
                [White "Green, Guy"]
                [Black "Club Opponent"]
                [Result "1/2-1/2"]

                1. d4 d5 2. c4 e6 1/2-1/2
                """;

        String once = CanonicalPgn.from(gameFrom(parse(original)));
        String twice = CanonicalPgn.from(gameFrom(parse(once)));

        assertThat(twice).isEqualTo(once);
    }

    @Test
    void survivesAGameThatEndsInCheckmate() {
        String mate = """
                [White "A"]
                [Black "B"]
                [Result "0-1"]

                1. f3 e5 2. g4 Qh4# 0-1
                """;

        ParsedGame first = parse(mate);

        assertThat(parse(CanonicalPgn.from(gameFrom(first)))).isEqualTo(first);
    }
}
