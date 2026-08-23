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
     * <p>Matching is on display name only. When the display name already exists,
     * the stored row is returned as-is and any differing {@code fideId} or
     * {@code federation} on the candidate is silently ignored — including a
     * candidate {@code fideId} that belongs to a different stored player. That
     * case is not a conflict by this method's contract: nothing is inserted, so
     * nothing collides.
     *
     * <p><b>Calling this inside an existing transaction detaches every entity
     * already loaded there</b> (the persistence adapter's upsert must clear the
     * persistence context so the read afterwards sees the just-inserted row).
     * A caller that holds other entities across this call must re-read anything
     * it intends to mutate afterwards.
     *
     * @throws PlayerIdentityConflict only when a NEW display name is being
     *                                inserted and the candidate's FIDE ID already
     *                                belongs to a different, existing player
     */
    Player createOrFind(NewPlayer candidate);
}
