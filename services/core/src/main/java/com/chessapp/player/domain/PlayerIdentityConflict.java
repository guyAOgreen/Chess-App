package com.chessapp.player.domain;

/**
 * The supplied identity data contradicts what is stored — for example a new
 * display name carrying a FIDE ID that already belongs to another player.
 *
 * <p>Unchecked, because a caller cannot usefully recover: the input is wrong, not
 * merely unlucky. This is not the concurrent-creation case, which
 * {@link PlayerRepository#createOrFind} handles without failing.
 */
public class PlayerIdentityConflict extends RuntimeException {

    public PlayerIdentityConflict(String message, Throwable cause) {
        super(message, cause);
    }
}
