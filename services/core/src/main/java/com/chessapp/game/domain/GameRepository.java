package com.chessapp.game.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Declared in the domain, implemented in persistence, so the dependency points
 * inward.
 *
 * <p>There is deliberately no update method. Movetext is immutable in ordinary
 * flows: correcting a confirmed move needs a dedicated, audited use case that
 * replaces the whole validated movetext atomically, and exposing a generic setter
 * here would let that happen implicitly instead.
 */
public interface GameRepository {

    /**
     * Persists a new game and returns it with the identity the database assigned.
     *
     * <p>Takes a {@link NewGame} rather than a {@link Game}, so a caller can never
     * hold an unsaved {@code Game} or choose its identifier.
     */
    Game save(NewGame candidate);

    Optional<Game> findById(UUID id);

    /**
     * One page of games matching the query, plus the size of the whole filtered
     * set.
     *
     * <p>Returns an empty page rather than an empty {@code Optional}: the collection
     * always exists, and it is the selection that can be empty.
     */
    GamePage find(GameQuery query);
}
