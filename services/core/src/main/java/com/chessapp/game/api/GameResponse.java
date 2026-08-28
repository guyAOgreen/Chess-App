package com.chessapp.game.api;

import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The game detail representation, returned by {@code GET /api/games/{id}}.
 *
 * <p>The detail representation only. The list endpoint returns
 * {@link GameSummaryResponse}, which is this shape without {@code movetext}: a page
 * of games would otherwise ship every game's complete move list to render a table
 * that displays none of them.
 *
 * <p>Built from the domain {@link Game}, so {@code GameEntity} never leaves
 * persistence.
 *
 * <p>Carries neither {@code sourcePgn} nor an assembled canonical document. The
 * viewer re-parses {@code movetext} to drive the board, so shipping the assembled
 * PGN would send the same moves twice in every response; and {@code sourcePgn} is
 * provenance rather than part of the resource. Export as a PGN file is a distinct
 * representation, decided when something needs it.
 *
 * <p>Optional metadata is present as null rather than omitted, so a client sees
 * one shape whatever the document said.
 */
public record GameResponse(UUID id,
                           Side white,
                           Side black,
                           String event,
                           String site,
                           String round,
                           LocalDate playedOn,
                           GameResult result,
                           String eco,
                           GameSource source,
                           String movetext) {

    /** One colour's share of the game. {@code name} is the game-time snapshot. */
    public record Side(UUID playerId, String name, Integer rating) {
    }

    public static GameResponse from(Game game) {
        return new GameResponse(game.id(),
                side(game.white()),
                side(game.black()),
                game.event(),
                game.site(),
                game.round(),
                game.playedOn(),
                game.result(),
                game.eco(),
                game.source(),
                game.movetext());
    }

    private static Side side(GameSide side) {
        return new Side(side.playerId(), side.name(), side.rating());
    }
}
