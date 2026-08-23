package com.chessapp.player.domain;

import java.util.UUID;

/**
 * A real-world chess player, who need not be an application user.
 *
 * <p>A {@code Player} always exists in the database, so {@code id} is never null.
 * Values are validated again on construction, so a corrupt or unexpectedly shaped
 * row cannot enter the domain unnoticed.
 */
public record Player(UUID id, String displayName, String fideId, String federation) {

    public Player {
        if (id == null) {
            throw new IllegalArgumentException("id is required; a Player is always persisted");
        }
        displayName = PlayerValues.displayName(displayName);
        fideId = PlayerValues.fideId(fideId);
        federation = PlayerValues.federation(federation);
    }
}
