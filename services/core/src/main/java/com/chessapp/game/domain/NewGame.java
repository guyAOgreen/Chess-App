package com.chessapp.game.domain;

import java.time.LocalDate;

/**
 * A validated request to create a game. Deliberately not a {@link Game}: it has
 * no identity, because identity is assigned by the database.
 *
 * <p>{@code sourcePgn} is the document a PGN import submitted, kept verbatim as
 * provenance and null for a scoresheet import, which has no source document.
 * Nothing reads it to answer a product question.
 */
public record NewGame(GameSide white,
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

    public NewGame {
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
