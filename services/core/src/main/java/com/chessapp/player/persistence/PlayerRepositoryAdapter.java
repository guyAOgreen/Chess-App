package com.chessapp.player.persistence;

import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.Player;
import com.chessapp.player.domain.PlayerIdentityConflict;
import com.chessapp.player.domain.PlayerRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PlayerRepositoryAdapter implements PlayerRepository {

    private static final String FIDE_ID_INDEX = "players_fide_id_key";

    private final PlayerJpaRepository jpa;

    PlayerRepositoryAdapter(PlayerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Player> findByDisplayName(String displayName) {
        return jpa.findByDisplayName(displayName).map(PlayerRepositoryAdapter::toDomain);
    }

    /**
     * Insert-then-read, in that order and as two statements.
     *
     * <p>Under READ COMMITTED the read takes a fresh snapshot, so a row committed
     * by a concurrent winner between the two statements is visible. Under
     * REPEATABLE READ it would not be, and this method would return empty,
     * surfacing as {@code IllegalStateException("player vanished between insert
     * and read")} below — a message pointing at a phantom delete rather than at
     * the real cause. The isolation level is a requirement, not an incidental
     * detail, so it is pinned explicitly here rather than left to inherit
     * PostgreSQL's default (which happens to also be READ COMMITTED, but nothing
     * would stop that default from changing under this code).
     *
     * <p><b>Caution for callers already inside a transaction:</b> see
     * {@link PlayerJpaRepository#insertIfAbsent} — this method clears the
     * persistence context as a side effect.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
