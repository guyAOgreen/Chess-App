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
     * <p>Safe to call part-way through your own unit of work: entities you have
     * already loaded stay managed, and you need declare nothing special on your
     * own {@code @Transactional} boundary. The one thing to know is that
     * resolving a player commits independently of your transaction, so a player
     * created here survives a later rollback of yours. That is intended — a
     * {@link Player} is shared reference data rather than part of any one
     * aggregate, and resolution is idempotent, so the row is simply reused.
     *
     * @throws PlayerIdentityConflict only when a NEW display name is being
     *                                inserted and the candidate's FIDE ID already
     *                                belongs to a different, existing player
     */
    Player createOrFind(NewPlayer candidate);
}
