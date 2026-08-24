package com.chessapp.chess;

import com.chessapp.game.domain.GameResult;
import java.time.LocalDate;

/**
 * The chess facts a PGN document yields, already in our own types.
 *
 * <p>Tags we do not model — {@code TimeControl}, {@code Termination},
 * {@code Annotator}, provider-specific ones — are not here. ADR 0002 keeps them
 * recoverable from {@code source_pgn}, which the import endpoint stores.
 *
 * <p>{@code movetext} satisfies the rules {@code Game.movetext} enforces: SAN with
 * move numbers, no tag pairs, no terminal result token.
 */
public record ParsedGame(String event,
                         String site,
                         LocalDate playedOn,
                         String round,
                         String whiteName,
                         String blackName,
                         Integer whiteRating,
                         Integer blackRating,
                         String eco,
                         GameResult result,
                         String movetext) {
}
