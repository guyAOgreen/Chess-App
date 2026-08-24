package com.chessapp.game.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A confirmed chess game.
 *
 * <p>{@code movetext} is canonical: validated SAN with move numbers, no tag pairs
 * and no terminal result token. The canonical PGN document is assembled on demand
 * from the metadata here plus {@code movetext}, so the tags have no stored form
 * that could go stale.
 *
 * <p>A {@code Game} always exists in the database, so {@code id} is never null.
 * Values are validated again on construction, so a corrupt or unexpectedly shaped
 * row cannot enter the domain unnoticed.
 */
public record Game(UUID id,
                   GameSide white,
                   GameSide black,
                   String event,
                   String site,
                   String round,
                   LocalDate playedOn,
                   GameResult result,
                   String eco,
                   GameSource source,
                   String movetext,
                   String sourcePgn) {

    public Game {
        if (id == null) {
            throw new IllegalArgumentException("id is required; a Game is always persisted");
        }
        white = GameValues.required(white, "white");
        black = GameValues.required(black, "black");
        event = GameValues.optionalTag(event);
        site = GameValues.optionalTag(site);
        round = GameValues.optionalTag(round);
        result = GameValues.required(result, "result");
        eco = GameValues.eco(eco);
        source = GameValues.required(source, "source");
        movetext = GameValues.movetext(movetext);
    }
}
