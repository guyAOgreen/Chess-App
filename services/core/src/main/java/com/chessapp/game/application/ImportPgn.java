package com.chessapp.game.application;

import com.chessapp.chess.ParsedGame;
import com.chessapp.chess.PgnParseResult;
import com.chessapp.chess.PgnParser;
import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GameRepository;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.NewGame;
import com.chessapp.player.application.FindOrCreatePlayer;
import com.chessapp.player.domain.Player;
import org.springframework.stereotype.Service;

/**
 * Turns a submitted PGN document into a stored {@link Game}.
 *
 * <p>Deliberately not {@code @Transactional}. {@code PlayerRepository.createOrFind}
 * is {@code REQUIRES_NEW} and commits independently of any caller, and
 * {@code GameRepository.save} is a single insert that is atomic on its own — so an
 * outer boundary would wrap nothing the adapters do not already cover, while
 * implying an atomicity that {@code REQUIRES_NEW} explicitly breaks. A reader
 * would reasonably infer that a failed insert rolls the players back. It does not.
 *
 * <p>Revisit if this ever writes more than one row of its own.
 */
@Service
public class ImportPgn {

    private final PgnParser parser;
    private final FindOrCreatePlayer findOrCreatePlayer;
    private final GameRepository games;

    public ImportPgn(PgnParser parser, FindOrCreatePlayer findOrCreatePlayer,
            GameRepository games) {
        this.parser = parser;
        this.findOrCreatePlayer = findOrCreatePlayer;
        this.games = games;
    }

    /** Never throws for bad input: an unusable document comes back as a rejection. */
    public PgnImportResult execute(String pgn) {
        // Parsing first means an invalid document never reaches the database, and
        // the common failure costs no connection.
        return switch (parser.parse(pgn)) {
            case PgnParseResult.Rejected rejected ->
                    new PgnImportResult.Rejected(rejected.error());
            case PgnParseResult.Parsed parsed -> store(parsed.game(), pgn);
        };
    }

    /**
     * The names written onto the game are the document's, not the resolved
     * players'. They are the same string today, because matching is exact on the
     * trimmed name, but they mean different things: {@code GameSide.name} is a
     * game-time snapshot, and taking it from the resolved player would start
     * rewriting history the day aliasing makes matching non-exact.
     *
     * <p>{@code sourcePgn} is the submitted value unchanged. ADR 0002 makes it
     * provenance that nothing reads to answer a product question, so normalising
     * it would defeat the point of keeping it.
     */
    private PgnImportResult store(ParsedGame parsed, String sourcePgn) {
        Player white = findOrCreatePlayer.execute(parsed.whiteName(), null, null);
        Player black = findOrCreatePlayer.execute(parsed.blackName(), null, null);
        Game game = games.save(new NewGame(
                new GameSide(white.id(), parsed.whiteName(), parsed.whiteRating()),
                new GameSide(black.id(), parsed.blackName(), parsed.blackRating()),
                parsed.event(),
                parsed.site(),
                parsed.round(),
                parsed.playedOn(),
                parsed.result(),
                parsed.eco(),
                GameSource.PGN_IMPORT,
                parsed.movetext(),
                sourcePgn));
        return new PgnImportResult.Imported(game);
    }
}
