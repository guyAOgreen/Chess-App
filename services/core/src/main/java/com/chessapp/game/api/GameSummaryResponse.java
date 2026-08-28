package com.chessapp.game.api;

import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.GameSummary;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A game as a row of the list: {@link GameResponse}'s shape without the moves.
 *
 * <p>A page of 25 games would otherwise ship 25 complete move lists to render a
 * table that displays none of them. {@code GET /api/games/{id}} returns the full
 * {@link GameResponse}, so the viewer still gets {@code movetext} when it opens one.
 *
 * <p>Reuses {@link GameResponse.Side} rather than redeclaring an identical nested
 * record: it is the same concept, in the same package, and a client should see one
 * shape for a player on a game whichever endpoint it came from.
 *
 * <p>Optional metadata is present as null rather than omitted, matching
 * {@code GameResponse} — a client sees one shape whatever the document said.
 */
public record GameSummaryResponse(UUID id,
                                  GameResponse.Side white,
                                  GameResponse.Side black,
                                  String event,
                                  String site,
                                  String round,
                                  LocalDate playedOn,
                                  GameResult result,
                                  String eco,
                                  GameSource source) {

    public static GameSummaryResponse from(GameSummary summary) {
        return new GameSummaryResponse(summary.id(),
                side(summary.white()),
                side(summary.black()),
                summary.event(),
                summary.site(),
                summary.round(),
                summary.playedOn(),
                summary.result(),
                summary.eco(),
                summary.source());
    }

    private static GameResponse.Side side(GameSide side) {
        return new GameResponse.Side(side.playerId(), side.name(), side.rating());
    }
}
