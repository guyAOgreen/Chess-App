package com.chessapp.game.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A game as a row of a list: everything {@link Game} carries except the moves.
 *
 * <p>{@code movetext} is absent by design rather than dropped later by a mapper.
 * It is a {@code TEXT} column, so a longer game's value is stored out of line and
 * reading it costs a separate fetch; selecting it for every row of every page would
 * pay that cost repeatedly to render a table that shows none of it. Because the
 * type has no field for it, the query cannot select it by accident.
 *
 * <p>{@code sourcePgn} is absent for ADR 0002's reason: it is provenance, and
 * nothing reads it to answer a product question.
 *
 * <p>Holds a {@link GameSide} per colour rather than six flat fields, so a summary
 * and a {@code Game} read the same way. That is why the projection is assembled
 * from a {@code Tuple} in persistence: the Jakarta Persistence specification does
 * not allow a compound selection as an argument to another, so a nested
 * {@code construct} for the two sides is not portable.
 *
 * <p>Only presence is checked. A summary is built from a row that was validated on
 * the way in, and the projection has no {@code movetext} to check the rules that
 * matter most.
 */
public record GameSummary(UUID id,
                          GameSide white,
                          GameSide black,
                          String event,
                          String site,
                          String round,
                          LocalDate playedOn,
                          GameResult result,
                          String eco,
                          GameSource source) {

    public GameSummary {
        if (id == null) {
            throw new IllegalArgumentException("id is required; a GameSummary is always persisted");
        }
        white = GameValues.required(white, "white");
        black = GameValues.required(black, "black");
        result = GameValues.required(result, "result");
        source = GameValues.required(source, "source");
    }
}
