package com.chessapp.game.domain;

import java.util.UUID;

/**
 * One colour's share of a game: who played it, what they were called at the time,
 * and their rating if it was recorded.
 *
 * <p>{@code playerId} drives identity and search. {@code name} is a game-time
 * snapshot used to assemble canonical PGN, so renaming or merging a
 * {@code Player} does not rewrite historical exports.
 */
public record GameSide(UUID playerId, String name, Integer rating) {

    public GameSide {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId is required");
        }
        name = GameValues.playerName(name);
        rating = GameValues.rating(rating);
    }
}
