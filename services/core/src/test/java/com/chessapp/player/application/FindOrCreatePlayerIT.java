package com.chessapp.player.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chessapp.player.domain.Player;
import com.chessapp.player.domain.PlayerIdentityConflict;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class FindOrCreatePlayerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private FindOrCreatePlayer findOrCreatePlayer;

    @Test
    void createsThePlayerOnFirstUse() {
        Player player = findOrCreatePlayer.execute("Adams, Michael", "400041", "ENG");

        assertThat(player.id()).isNotNull();
        assertThat(player.displayName()).isEqualTo("Adams, Michael");
    }

    @Test
    void returnsTheSamePlayerOnSecondUse() {
        Player first = findOrCreatePlayer.execute("Howell, David", null, "ENG");
        Player second = findOrCreatePlayer.execute("Howell, David", null, "ENG");

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void trimsThePgnTagValueBeforeMatching() {
        Player padded = findOrCreatePlayer.execute("  McShane, Luke  ", null, null);
        Player exact = findOrCreatePlayer.execute("McShane, Luke", null, null);

        assertThat(padded.displayName()).isEqualTo("McShane, Luke");
        assertThat(exact.id()).isEqualTo(padded.id());
    }

    @Test
    void rejectsThePgnUnknownPlayerMarker() {
        assertThatThrownBy(() -> findOrCreatePlayer.execute("?", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void reportsAnIdentityConflictWhenAFideIdBelongsToAnotherPlayer() {
        findOrCreatePlayer.execute("Jones, Gawain", "409561", "ENG");

        assertThatThrownBy(() -> findOrCreatePlayer.execute("Jones, G.", "409561", "ENG"))
                .isInstanceOf(PlayerIdentityConflict.class);
    }

    /**
     * Two callers racing for the same new player must both receive the same row.
     *
     * <p>Separate threads mean separate pooled connections, and therefore separate
     * transactions — a single connection would serialise the statements and there
     * would be no race to test. The latch releases both at once to maximise
     * overlap.
     *
     * <p>The assertion cannot fail spuriously: whatever the interleaving, two calls
     * for one name must yield one id. If the race does not happen to occur on a
     * given run the test simply proves less, so it is run repeatedly rather than
     * made timing-dependent.
     */
    @Test
    void concurrentCallsForTheSameNameReturnOneIdentity() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            String name = "Racer " + attempt;
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Callable<Player> call = () -> {
                    start.await();
                    return findOrCreatePlayer.execute(name, null, null);
                };
                Future<Player> left = pool.submit(call);
                Future<Player> right = pool.submit(call);

                start.countDown();

                assertThat(List.of(left.get().id(), right.get().id()))
                        .as("both racers must resolve to the same player")
                        .containsOnly(left.get().id());
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
