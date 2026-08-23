package com.chessapp.player.persistence;

import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.Player;
import com.chessapp.player.domain.PlayerIdentityConflict;
import com.chessapp.player.domain.PlayerRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PlayerRepositoryAdapter implements PlayerRepository {

    private static final String FIDE_ID_INDEX = "players_fide_id_idx";

    private final PlayerJpaRepository jpa;

    PlayerRepositoryAdapter(PlayerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Player> findByDisplayName(String displayName) {
        if (displayName == null) {
            return Optional.empty();
        }
        return jpa.findByDisplayName(displayName.trim()).map(PlayerRepositoryAdapter::toDomain);
    }

    /**
     * Insert-then-read, in that order and as two statements.
     *
     * <p>Under READ COMMITTED the read takes a fresh snapshot, so a row committed
     * by a concurrent winner between the two statements is visible. Under
     * REPEATABLE READ it would not be, and this method would return empty,
     * surfacing as {@code IllegalStateException("player vanished between insert
     * and read")} below — a message pointing at a phantom delete rather than at
     * the real cause.
     *
     * <p>{@code REQUIRES_NEW} is what makes that guarantee real. Spring honours a
     * declared isolation level only on the transaction it actually starts, so
     * under the default {@code REQUIRED} propagation a caller's open transaction
     * would be joined and the level here silently ignored. Starting a new
     * transaction keeps the requirement inside this adapter, where it belongs,
     * instead of obliging every caller to declare the same level.
     *
     * <p>The trade-off is that resolving a player commits independently of the
     * caller: a subsequently failed import leaves the player row behind. That is
     * intended. A {@code Player} is shared reference data rather than part of any
     * one game, resolution is idempotent, and the next import of that name reuses
     * the row.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Player createOrFind(NewPlayer candidate) {
        try {
            jpa.insertIfAbsent(candidate.displayName(), candidate.fideId(), candidate.federation());
        } catch (DataIntegrityViolationException e) {
            if (mentions(e, FIDE_ID_INDEX)) {
                throw new PlayerIdentityConflict(
                        "FIDE ID " + candidate.fideId() + " already belongs to a different player",
                        e);
            }
            throw e;
        }
        return jpa.findByDisplayName(candidate.displayName())
                .map(PlayerRepositoryAdapter::toDomain)
                .orElseThrow(() -> new IllegalStateException(
                        "player vanished between insert and read: " + candidate.displayName()));
    }

    private static Player toDomain(PlayerEntity entity) {
        return new Player(entity.getId(), entity.getDisplayName(), entity.getFideId(),
                entity.getFederation());
    }

    /**
     * Walks the cause chain looking for the index name. The constraint name is not
     * reliably exposed as a field by every driver and dialect combination, so the
     * message is checked too.
     */
    private static boolean mentions(Throwable throwable, String name) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && name.equals(violation.getConstraintName())) {
                return true;
            }
            if (cause.getMessage() != null && cause.getMessage().contains(name)) {
                return true;
            }
        }
        return false;
    }
}
