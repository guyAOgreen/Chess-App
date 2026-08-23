package com.chessapp.player.persistence;

import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.Player;
import com.chessapp.player.domain.PlayerIdentityConflict;
import com.chessapp.player.domain.PlayerRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
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
     * <p>Under READ COMMITTED — PostgreSQL's default — the read takes a fresh
     * snapshot, so a row committed by a concurrent winner between the two
     * statements is visible. Under REPEATABLE READ it would not be, and this
     * method would return empty; the isolation level is a requirement, not an
     * incidental detail.
     */
    @Override
    @Transactional
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
