package com.chessapp.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.Player;
import com.chessapp.player.domain.PlayerIdentityConflict;
import com.chessapp.player.domain.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class PlayerRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private PlayerRepository players;

    @Test
    void createReturnsAPersistedPlayerWithADatabaseAssignedId() {
        Player created = players.createOrFind(new NewPlayer("Carlsen, Magnus", "1503014", "NOR"));

        assertThat(created.id()).isNotNull();
        assertThat(created.id().version()).isEqualTo(7);
        assertThat(created.displayName()).isEqualTo("Carlsen, Magnus");
        assertThat(created.fideId()).isEqualTo("1503014");
        assertThat(created.federation()).isEqualTo("NOR");
    }

    @Test
    void createOrFindIsIdempotentForTheSameDisplayName() {
        Player first = players.createOrFind(new NewPlayer("Green, Guy", null, "ENG"));
        Player second = players.createOrFind(new NewPlayer("Green, Guy", null, "ENG"));

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void createOrFindReturnsTheStoredValuesWhenTheCandidateDiffers() {
        Player stored = players.createOrFind(new NewPlayer("Polgar, Judit", "700070", "HUN"));
        Player found = players.createOrFind(new NewPlayer("Polgar, Judit", null, null));

        assertThat(found.id()).isEqualTo(stored.id());
        assertThat(found.fideId()).isEqualTo("700070");
        assertThat(found.federation()).isEqualTo("HUN");
    }

    @Test
    void findByDisplayNameReturnsEmptyForAnUnknownName() {
        assertThat(players.findByDisplayName("Nobody At All")).isEmpty();
    }

    @Test
    void findByDisplayNameReturnsTheStoredPlayer() {
        Player created = players.createOrFind(new NewPlayer("Kasparov, Garry", "4100018", "RUS"));

        assertThat(players.findByDisplayName("Kasparov, Garry"))
                .contains(created);
    }

    @Test
    void findByDisplayNameTrimsTheArgument() {
        Player created = players.createOrFind(new NewPlayer("Nakamura, Hikaru", null, "USA"));

        assertThat(players.findByDisplayName("  Nakamura, Hikaru  "))
                .contains(created);
    }

    @Test
    void matchingIsCaseSensitive() {
        Player upper = players.createOrFind(new NewPlayer("Short, Nigel", null, "ENG"));
        Player lower = players.createOrFind(new NewPlayer("short, nigel", null, "ENG"));

        assertThat(lower.id()).isNotEqualTo(upper.id());
    }

    @Test
    void aDifferentNameReusingAFideIdIsAnIdentityConflictRatherThanAMatch() {
        players.createOrFind(new NewPlayer("Smith, John", "2000123", null));

        assertThatThrownBy(() -> players.createOrFind(new NewPlayer("Smith, J.", "2000123", null)))
                .isInstanceOf(PlayerIdentityConflict.class)
                .hasMessageContaining("2000123");
    }

    @Test
    void anExistingDisplayNameReturnsTheStoredRowEvenWhenTheCandidateFideIdBelongsToAnotherPlayer() {
        Player first = players.createOrFind(new NewPlayer("Anand, Viswanathan", "5000017", "IND"));
        players.createOrFind(new NewPlayer("Carlsen, Magnus", "1503014", "NOR"));

        Player found = players.createOrFind(
                new NewPlayer("Anand, Viswanathan", "1503014", "NOR"));

        assertThat(found.id()).isEqualTo(first.id());
        assertThat(found.displayName()).isEqualTo(first.displayName());
        assertThat(found.fideId()).isEqualTo(first.fideId());
        assertThat(found.federation()).isEqualTo(first.federation());
    }
}
