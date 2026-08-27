package com.chessapp.game.persistence;

import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GamePage;
import com.chessapp.game.domain.GameQuery;
import com.chessapp.game.domain.GameRepository;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.NewGame;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class GameRepositoryAdapter implements GameRepository {

    private final GameJpaRepository jpa;
    private final GameSearchQuery search;

    GameRepositoryAdapter(GameJpaRepository jpa, GameSearchQuery search) {
        this.jpa = jpa;
        this.search = search;
    }

    /**
     * The returned {@link Game} is built from the candidate plus the generated id
     * rather than read back. The candidate is already normalised and validated, and
     * the columns hold exactly what was bound, so a re-read could only agree —
     * {@code GameRepositoryIT} asserts that it does.
     */
    @Override
    @Transactional
    public Game save(NewGame candidate) {
        UUID id = jpa.insertReturningId(
                candidate.white().playerId(),
                candidate.black().playerId(),
                candidate.white().name(),
                candidate.black().name(),
                candidate.white().rating(),
                candidate.black().rating(),
                candidate.event(),
                candidate.site(),
                candidate.round(),
                candidate.playedOn(),
                candidate.result().name(),
                candidate.eco(),
                candidate.source().name(),
                candidate.movetext(),
                candidate.sourcePgn());
        return new Game(id, candidate.white(), candidate.black(), candidate.event(),
                candidate.site(), candidate.round(), candidate.playedOn(), candidate.result(),
                candidate.eco(), candidate.source(), candidate.movetext(), candidate.sourcePgn());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Game> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpa.findById(id).map(GameRepositoryAdapter::toDomain);
    }

    /**
     * {@code readOnly} for one connection checkout instead of two and to keep
     * Hibernate from dirty-checking a read — and explicitly <em>not</em> for
     * consistency between the two statements it covers. Under PostgreSQL's default
     * {@code READ COMMITTED} isolation each statement takes its own snapshot, so a
     * concurrent write can still leave the rows and the total disagreeing. Closing
     * that would need repeatable-read isolation, and nothing yet needs it.
     */
    @Override
    @Transactional(readOnly = true)
    public GamePage find(GameQuery query) {
        return search.run(query);
    }

    private static Game toDomain(GameEntity entity) {
        return new Game(entity.getId(),
                new GameSide(entity.getWhitePlayerId(), entity.getWhiteName(),
                        entity.getWhiteRating()),
                new GameSide(entity.getBlackPlayerId(), entity.getBlackName(),
                        entity.getBlackRating()),
                entity.getEvent(), entity.getSite(), entity.getRound(), entity.getPlayedOn(),
                entity.getResult(), entity.getEco(), entity.getSource(), entity.getMovetext(),
                entity.getSourcePgn());
    }
}
