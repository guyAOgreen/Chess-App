package com.chessapp.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GameRepository;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.NewGame;
import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.PlayerRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class GameRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private GameRepository games;

    @Autowired
    private PlayerRepository players;

    private GameSide white;
    private GameSide black;

    @BeforeEach
    void resolvePlayers() {
        UUID whiteId = players.createOrFind(new NewPlayer("Green, Guy", null, "ENG")).id();
        UUID blackId = players.createOrFind(new NewPlayer("Club Opponent", null, null)).id();
        white = new GameSide(whiteId, "Green, Guy", 1850);
        black = new GameSide(blackId, "Club Opponent", 1720);
    }

    private NewGame fullyPopulated() {
        return new NewGame(white, black, "Club Championship", "London ENG", "3",
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "C60", GameSource.PGN_IMPORT,
                "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6", "[Event \"Club Championship\"]\n\n1. e4 e5 1-0\n");
    }

    @Test
    void saveReturnsAPersistedGameWithADatabaseAssignedId() {
        Game saved = games.save(fullyPopulated());

        assertThat(saved.id()).isNotNull();
        assertThat(saved.id().version()).isEqualTo(7);
    }

    @Test
    void findByIdReturnsEveryStoredValue() {
        NewGame candidate = fullyPopulated();

        Game found = games.findById(games.save(candidate).id()).orElseThrow();

        assertThat(found.white()).isEqualTo(white);
        assertThat(found.black()).isEqualTo(black);
        assertThat(found.event()).isEqualTo("Club Championship");
        assertThat(found.site()).isEqualTo("London ENG");
        assertThat(found.round()).isEqualTo("3");
        assertThat(found.playedOn()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(found.result()).isEqualTo(GameResult.WHITE_WON);
        assertThat(found.eco()).isEqualTo("C60");
        assertThat(found.source()).isEqualTo(GameSource.PGN_IMPORT);
        assertThat(found.movetext()).isEqualTo("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
        assertThat(found.sourcePgn()).isEqualTo(candidate.sourcePgn());
    }

    @Test
    void roundTripsAScoresheetImportWhichHasNoOptionalMetadataAndNoSourceDocument() {
        NewGame candidate = new NewGame(
                new GameSide(white.playerId(), "Green, Guy", null),
                new GameSide(black.playerId(), "Club Opponent", null),
                null, null, null, null, GameResult.DRAW, null, GameSource.PERSONAL,
                "1. d4 d5 2. c4 e6", null);

        Game found = games.findById(games.save(candidate).id()).orElseThrow();

        assertThat(found.white().rating()).isNull();
        assertThat(found.event()).isNull();
        assertThat(found.site()).isNull();
        assertThat(found.round()).isNull();
        assertThat(found.playedOn()).isNull();
        assertThat(found.eco()).isNull();
        assertThat(found.sourcePgn()).isNull();
        assertThat(found.result()).isEqualTo(GameResult.DRAW);
        assertThat(found.source()).isEqualTo(GameSource.PERSONAL);
    }

    @Test
    void saveReturnsTheSameValuesThatAreSubsequentlyRead() {
        Game saved = games.save(fullyPopulated());

        assertThat(games.findById(saved.id())).contains(saved);
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(games.findById(UUID.randomUUID())).isEmpty();
    }
}
