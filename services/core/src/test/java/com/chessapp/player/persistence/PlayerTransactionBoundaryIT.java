package com.chessapp.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.Player;
import com.chessapp.player.domain.PlayerRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves that resolving a player does not disturb the caller's unit of work.
 *
 * <p>PGN import (#7) will call {@link PlayerRepository#createOrFind} part-way
 * through assembling a game, holding its own managed entities and its own
 * transaction. Two earlier versions of this adapter would have broken that
 * caller: one cleared the persistence context, detaching everything the caller
 * had loaded, and one required every caller to declare a matching isolation
 * level. Both are pinned shut here.
 */
@Testcontainers
@SpringBootTest
class PlayerTransactionBoundaryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private PlayerRepository players;

    @Autowired
    private OuterUnitOfWork outer;

    @Test
    void aPlainTransactionalCallerNeedNotDeclareAnIsolationLevel() {
        Player existing = players.createOrFind(new NewPlayer("Kramnik, Vladimir", null, "RUS"));

        assertThatCode(() -> outer.resolveAnotherPlayer(existing.id(), "Leko, Peter"))
                .doesNotThrowAnyException();
    }

    @Test
    void entitiesTheCallerLoadedStayManagedAcrossTheCall() {
        Player existing = players.createOrFind(new NewPlayer("Topalov, Veselin", null, "BUL"));

        boolean stillManaged = outer.resolveAnotherPlayer(existing.id(), "Ivanchuk, Vassily");

        assertThat(stillManaged)
                .as("an entity loaded before createOrFind must still be managed after it, "
                        + "or the caller's later changes are silently dropped")
                .isTrue();
    }

    @TestConfiguration
    static class Config {

        @Bean
        OuterUnitOfWork outerUnitOfWork(PlayerRepository players, EntityManager entityManager) {
            return new OuterUnitOfWork(players, entityManager);
        }
    }

    /**
     * Stands in for PGN import: opens its own transaction with a plain
     * {@code @Transactional}, loads an entity, then resolves a player. A separate
     * bean because Spring's transaction proxy does not apply to self-invocation.
     */
    static class OuterUnitOfWork {

        private final PlayerRepository players;
        private final EntityManager entityManager;

        OuterUnitOfWork(PlayerRepository players, EntityManager entityManager) {
            this.players = players;
            this.entityManager = entityManager;
        }

        /**
         * @return whether the entity loaded before the call is still managed after it
         */
        @Transactional
        boolean resolveAnotherPlayer(UUID alreadyLoadedId, String nameToResolve) {
            PlayerEntity loadedFirst = entityManager.find(PlayerEntity.class, alreadyLoadedId);
            assertThat(loadedFirst).isNotNull();
            assertThat(entityManager.contains(loadedFirst)).isTrue();

            players.createOrFind(new NewPlayer(nameToResolve, null, null));

            return entityManager.contains(loadedFirst);
        }
    }
}
