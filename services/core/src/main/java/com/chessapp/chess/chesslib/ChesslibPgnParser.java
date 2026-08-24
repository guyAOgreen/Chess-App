package com.chessapp.chess.chesslib;

import com.chessapp.chess.ParsedGame;
import com.chessapp.chess.PgnError;
import com.chessapp.chess.PgnErrorCode;
import com.chessapp.chess.PgnParseResult;
import com.chessapp.chess.PgnParser;
import com.chessapp.chess.PgnTagReader;
import com.chessapp.chess.PgnTagValues;
import com.chessapp.game.domain.GameResult;
import com.github.bhlangonijr.chesslib.game.Game;
import com.github.bhlangonijr.chesslib.pgn.PgnIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The only {@link PgnParser} implementation, and with {@link ValidatedMoves} the
 * only place chesslib is used.
 *
 * <p>chesslib decodes SAN and strips annotations; it decides nothing. Tags are read
 * from the document by {@link PgnTagReader} because the library's parsed model
 * loses {@code Round} and cannot distinguish a missing {@code Result} from
 * {@code *}. Legality is decided by {@link ValidatedMoves}.
 *
 * <p>Checks run in a fixed order — structure, moves, players, result — so the same
 * document always yields the same error. Moves come before the player and result
 * checks because a game that does not reconstruct is broken in a way the user has
 * to fix first, and because the result check needs the final position.
 */
@Component
public class ChesslibPgnParser implements PgnParser {

    /** The PGN marker for an unknown tag value. */
    private static final String UNKNOWN = "?";

    @Override
    public PgnParseResult parse(String pgn) {
        if (pgn == null || pgn.isBlank()) {
            return rejected(PgnErrorCode.NOT_PGN, "no PGN text was supplied");
        }

        // Computed from the ORIGINAL pgn argument and never reassigned: everything
        // downstream that decides the declared result (the NO_MOVES check, the
        // UNREADABLE_MOVE guard, and result resolution at the bottom of this
        // method) must keep reading what the document actually said, not the
        // token readGames may append below purely to make chesslib parse the
        // moves. Do not let a normalised copy leak into these variables.
        String movetext = PgnTagReader.movetext(pgn);
        String terminalToken = terminalToken(movetext);

        List<Game> games;
        try {
            games = readGames(pgn, terminalToken != null);
        } catch (RuntimeException unreadable) {
            return rejected(PgnErrorCode.NOT_PGN,
                    "the text could not be read as PGN: " + unreadable.getMessage());
        }
        if (games.isEmpty()) {
            return rejected(PgnErrorCode.NOT_PGN, "no game was found in the text");
        }
        if (games.size() > 1) {
            return rejected(PgnErrorCode.MULTIPLE_GAMES,
                    "the file holds " + games.size() + " games; import one game at a time");
        }

        Game game = games.get(0);
        Map<String, String> tags = PgnTagReader.tags(pgn);
        // chesslib parses arbitrary text with no tag pairs into a Game with zero
        // half-moves rather than throwing (verified empirically): "this is not a
        // chess game" yields fen=null, property=null and an empty half-move list
        // with no exception anywhere. A document carrying no tag pairs at all is
        // not recognisable PGN structure, whatever chesslib's lenient reader made
        // of it, so this is decided from our own tag reader rather than trusting
        // the library to have rejected it already.
        if (tags.isEmpty()) {
            return rejected(PgnErrorCode.NOT_PGN, "no PGN tag pairs were found in the text");
        }
        if (game.getFen() != null || tags.containsKey("FEN") || tags.containsKey("SetUp")) {
            return rejected(PgnErrorCode.NON_STANDARD_START_POSITION,
                    "games that start from a position other than the standard one are not"
                            + " supported yet");
        }

        if (withoutTerminalToken(movetext, terminalToken).isBlank()) {
            return rejected(PgnErrorCode.NO_MOVES, "the game has no moves");
        }

        ValidatedMoves moves;
        try {
            // Game.loadMoveText() declares "throws Exception" in chesslib 1.3.7,
            // so the catch below is Exception rather than RuntimeException.
            game.loadMoveText();
            // chesslib only parses movetext during iteration when the source line
            // ends with a result token; readGames guarantees that now, so a
            // no-terminal-token document can no longer silently reach here with
            // zero half-moves. But comment-only, NAG-only or bare-move-number
            // movetext ("{no moves here} *", "$1 *", "1. *") passes the earlier
            // non-blank text check yet still parses to zero half-moves without
            // throwing (verified empirically) — this guard is what catches that,
            // rather than reporting a game with no real moves as a success.
            if (game.getHalfMoves().isEmpty()) {
                return rejected(PgnErrorCode.UNREADABLE_MOVE,
                        "the moves could not be read from the movetext");
            }
            moves = ValidatedMoves.of(game.getHalfMoves());
        } catch (IllegalMoveAtPly illegal) {
            return rejected(PgnErrorCode.ILLEGAL_MOVE, illegal.getMessage(), illegal.ply());
        } catch (Exception unreadable) {
            return rejected(PgnErrorCode.UNREADABLE_MOVE,
                    "a move could not be understood: " + unreadable.getMessage());
        }

        PgnParseResult playerProblem = checkPlayers(tags);
        if (playerProblem != null) {
            return playerProblem;
        }

        GameResult fromTag = GameResult.fromPgnToken(tags.get("Result"));
        GameResult fromToken = GameResult.fromPgnToken(terminalToken);
        if (fromTag != null && fromToken != null && fromTag != fromToken) {
            return rejected(PgnErrorCode.RESULT_CONFLICT,
                    "the Result tag says " + fromTag.pgnToken() + " but the moves end with "
                            + fromToken.pgnToken());
        }
        GameResult declared = fromTag != null ? fromTag : fromToken;
        if (declared == null) {
            return rejected(PgnErrorCode.RESULT_MISSING,
                    "the game declares no result, as a Result tag or as a token after the moves");
        }
        if (moves.terminalResult() != null && moves.terminalResult() != declared) {
            return rejected(PgnErrorCode.RESULT_CONTRADICTS_POSITION,
                    "the game declares " + declared.pgnToken() + " but the final position is "
                            + moves.terminalResult().pgnToken());
        }

        return new PgnParseResult.Parsed(new ParsedGame(
                PgnTagValues.optional(tags.get("Event")),
                PgnTagValues.optional(tags.get("Site")),
                PgnTagValues.date(tags.get("Date")),
                PgnTagValues.optional(tags.get("Round")),
                tags.get("White").trim(),
                tags.get("Black").trim(),
                PgnTagValues.rating(tags.get("WhiteElo")),
                PgnTagValues.rating(tags.get("BlackElo")),
                PgnTagValues.eco(tags.get("ECO")),
                declared,
                moves.movetext()));
    }

    /**
     * The iterator validates lazily and throws from iteration itself, so the whole
     * loop is inside the caller's error handling rather than only the move loading.
     *
     * <p>chesslib only parses a game's movetext during iteration when the source
     * text ends with one of the four PGN result tokens; without one,
     * {@code Game.getMoveText()} stays null and the later {@code loadMoveText()}
     * call is a documented no-op that neither parses anything nor throws (verified
     * empirically: a document with real, legal moves but no trailing token yields
     * zero half-moves and no error). So when the caller reports the document has
     * no terminal token of its own, a {@code *} is appended to a LOCAL COPY of the
     * text before handing it to chesslib, purely to make the library's parse gate
     * fire. The caller's own reading of the document — what result it actually
     * declared — must never be derived from this copy.
     */
    private static List<Game> readGames(String pgn, boolean hasTerminalToken) {
        String forChesslib = hasTerminalToken ? pgn : pgn.stripTrailing() + " *";
        List<Game> games = new ArrayList<>();
        try (PgnIterator iterator = new PgnIterator(List.of(forChesslib.split("\\R", -1)))) {
            for (Game game : iterator) {
                games.add(game);
            }
        } catch (RuntimeException rethrow) {
            throw rethrow;
        } catch (Exception closeFailed) {
            throw new IllegalStateException(closeFailed);
        }
        return games;
    }

    /**
     * A name we cannot write as a PGN tag is a player we cannot identify: it would
     * otherwise pass through to {@code Game} construction, which rejects control
     * characters in player names, turning a bad-input case into an
     * {@code IllegalArgumentException} deep in domain construction instead of a
     * clean rejection here.
     */
    private static PgnParseResult checkPlayers(Map<String, String> tags) {
        for (String colour : new String[] {"White", "Black"}) {
            String name = tags.get(colour);
            if (name == null || name.isBlank() || UNKNOWN.equals(name.trim())
                    || name.chars().anyMatch(Character::isISOControl)) {
                return rejected(PgnErrorCode.PLAYER_UNKNOWN,
                        "the " + colour + " player is not named; a game with an unknown player"
                                + " cannot be stored");
            }
        }
        return null;
    }

    private static String terminalToken(String movetext) {
        if (movetext.isBlank()) {
            return null;
        }
        String[] tokens = movetext.trim().split("\\s+");
        String last = tokens[tokens.length - 1];
        return GameResult.fromPgnToken(last) != null ? last : null;
    }

    private static String withoutTerminalToken(String movetext, String terminalToken) {
        if (terminalToken == null) {
            return movetext;
        }
        return movetext.substring(0, movetext.lastIndexOf(terminalToken));
    }

    private static PgnParseResult rejected(PgnErrorCode code, String message) {
        return new PgnParseResult.Rejected(new PgnError(code, message));
    }

    private static PgnParseResult rejected(PgnErrorCode code, String message, Integer ply) {
        return new PgnParseResult.Rejected(new PgnError(code, message, ply));
    }
}
