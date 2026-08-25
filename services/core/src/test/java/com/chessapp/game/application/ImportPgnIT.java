package com.chessapp.game.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.chess.PgnErrorCode;
import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GameRepository;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSource;
import com.chessapp.player.domain.PlayerRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class ImportPgnIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ImportPgn importPgn;

    @Autowired
    private GameRepository games;

    @Autowired
    private PlayerRepository players;

    /**
     * The container is shared across the class with no cleanup between methods, so
     * each test names its own players. Reusing a name would couple tests through
     * the players table.
     */
    private static String pgn(String white, String black) {
        return """
                [Event "Club Championship"]
                [Site "London ENG"]
                [Date "2026.03.14"]
                [Round "3.2"]
                [White "%s"]
                [Black "%s"]
                [Result "1-0"]
                [WhiteElo "1850"]
                [ECO "C60"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """.formatted(white, black);
    }

    private Game imported(String pgn) {
        PgnImportResult result = importPgn.execute(pgn);
        assertThat(result).isInstanceOf(PgnImportResult.Imported.class);
        return ((PgnImportResult.Imported) result).game();
    }

    @Test
    void storesEveryFactTheDocumentDeclared() {
        Game game = imported(pgn("Facts White", "Facts Black"));

        assertThat(game.id()).isNotNull();
        assertThat(game.white().name()).isEqualTo("Facts White");
        assertThat(game.white().rating()).isEqualTo(1850);
        assertThat(game.black().name()).isEqualTo("Facts Black");
        assertThat(game.black().rating()).isNull();
        assertThat(game.event()).isEqualTo("Club Championship");
        assertThat(game.site()).isEqualTo("London ENG");
        assertThat(game.round()).isEqualTo("3.2");
        assertThat(game.playedOn()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(game.result()).isEqualTo(GameResult.WHITE_WON);
        assertThat(game.eco()).isEqualTo("C60");
        assertThat(game.source()).isEqualTo(GameSource.PGN_IMPORT);
        assertThat(game.movetext()).isEqualTo("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
    }
}
