package com.chessapp.player.domain;

/**
 * A validated request to create a player. Deliberately not a {@link Player}:
 * it has no identity, because identity is assigned by the database.
 */
public record NewPlayer(String displayName, String fideId, String federation) {

    public NewPlayer {
        displayName = PlayerValues.displayName(displayName);
        fideId = PlayerValues.fideId(fideId);
        federation = PlayerValues.federation(federation);
    }
}
