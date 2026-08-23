package com.chessapp.player.domain;

import java.util.Optional;

/**
 * Declared in the domain, implemented in persistence, so the dependency points
 * inward. There is deliberately no {@code save(Player)}: identity is assigned by
 * the database, so a caller can never hold an unsaved {@link Player}.
 */
public interface PlayerRepository {

    /** Exact, case-sensitive match on the trimmed display name. */
    Optional<Player> findByDisplayName(String displayName);

    /**
     * Returns the existing player with this display name, or creates one.
     *
     * <p>Safe under concurrent callers: two simultaneous calls for the same name
     * return the same player rather than one of them failing.
     *
     * @throws PlayerIdentityConflict if the candidate's FIDE ID already belongs
     *                                to a player with a different display name
     */
    Player createOrFind(NewPlayer candidate);
}
