package com.chessapp.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The database enforces the same rules the domain does, so a row written by
 * anything other than the domain — a migration, a fix-up script, a future code
 * path — cannot violate them silently.
 */
@Testcontainers
@SpringBootTest
class GameSchemaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DataSource dataSource;

    private UUID whiteId;
    private UUID blackId;

    @BeforeEach
    void createPlayers() throws SQLException {
        whiteId = insertPlayer("Schema White " + UUID.randomUUID());
        blackId = insertPlayer("Schema Black " + UUID.randomUUID());
    }

    private UUID insertPlayer(String displayName) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO players (display_name) VALUES (?) RETURNING id")) {
            statement.setString(1, displayName);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    /** A valid row, so each test can vary the one column it is about. */
    private Map<String, Object> validRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("white_player_id", whiteId);
        row.put("black_player_id", blackId);
        row.put("white_name", "Green, Guy");
        row.put("black_name", "Club Opponent");
        row.put("white_rating", 1850);
        row.put("black_rating", null);
        row.put("event", "Club Championship");
        row.put("site", "London ENG");
        row.put("round", "3");
        row.put("played_on", java.sql.Date.valueOf(LocalDate.of(2026, 3, 14)));
        row.put("result", "WHITE_WON");
        row.put("eco", "C60");
        row.put("source", "PGN_IMPORT");
        row.put("movetext", "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
        row.put("source_pgn", null);
        return row;
    }

    private UUID insert(Map<String, Object> row) throws SQLException {
        String columns = String.join(", ", row.keySet());
        String placeholders = String.join(", ", row.keySet().stream().map(c -> "?").toList());
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO games (" + columns + ") VALUES (" + placeholders
                             + ") RETURNING id")) {
            int index = 1;
            for (Object value : row.values()) {
                statement.setObject(index++, value);
            }
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private UUID insertWith(String column, Object value) throws SQLException {
        Map<String, Object> row = validRow();
        row.put(column, value);
        return insert(row);
    }

    @Test
    void assignsATimeOrderedUuidWithoutTheCallerSupplyingOne() throws SQLException {
        UUID id = insert(validRow());

        assertThat(id).isNotNull();
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void acceptsAGameWithNoOptionalMetadataAtAll() throws SQLException {
        Map<String, Object> row = validRow();
        for (String column : new String[] {
                "white_rating", "event", "site", "round", "played_on", "eco", "source_pgn"}) {
            row.put(column, null);
        }

        assertThat(insert(row)).isNotNull();
    }

    @Test
    void rejectsAGameWhoseWhitePlayerDoesNotExist() {
        assertThatThrownBy(() -> insertWith("white_player_id", UUID.randomUUID()))
                .hasMessageContaining("games_white_player_id_fkey");
    }

    @Test
    void rejectsAGameWhoseBlackPlayerDoesNotExist() {
        assertThatThrownBy(() -> insertWith("black_player_id", UUID.randomUUID()))
                .hasMessageContaining("games_black_player_id_fkey");
    }

    @Test
    void rejectsAnUnrecognisedResult() {
        assertThatThrownBy(() -> insertWith("result", "WHITE_RESIGNED"))
                .hasMessageContaining("games_result_valid");
    }

    @Test
    void rejectsAnUnrecognisedSource() {
        assertThatThrownBy(() -> insertWith("source", "SMARTPHONE"))
                .hasMessageContaining("games_source_valid");
    }

    @Test
    void rejectsABlankGameTimeName() {
        assertThatThrownBy(() -> insertWith("white_name", "   "))
                .hasMessageContaining("games_white_name");
    }

    @Test
    void rejectsThePgnUnknownMarkerAsAGameTimeName() {
        assertThatThrownBy(() -> insertWith("black_name", "?"))
                .hasMessageContaining("games_black_name_not_unknown");
    }

    @Test
    void rejectsANonPositiveRating() {
        assertThatThrownBy(() -> insertWith("white_rating", 0))
                .hasMessageContaining("games_white_rating_positive");
    }

    @Test
    void rejectsAMalformedEcoCode() {
        assertThatThrownBy(() -> insertWith("eco", "F60"))
                .hasMessageContaining("games_eco_format");
    }

    @Test
    void rejectsBlankMovetext() {
        assertThatThrownBy(() -> insertWith("movetext", "   "))
                .hasMessageContaining("games_movetext");
    }

    @Test
    void rejectsMovetextContainingTagPairs() {
        assertThatThrownBy(() -> insertWith("movetext", "[Event \"Club\"]\n\n1. e4 e5"))
                .hasMessageContaining("games_movetext_no_tag_pairs");
    }

    @Test
    void rejectsMovetextEndingInAResultToken() {
        assertThatThrownBy(() -> insertWith("movetext", "1. e4 e5 2. Nf3 1-0"))
                .hasMessageContaining("games_movetext_no_result_token");
    }

    @Test
    void rejectsAResultTokenSeparatedByALineBreakJustAsTheDomainDoes() {
        assertThatThrownBy(() -> insertWith("movetext", "1. e4 e5 2. Nf3 Nc6\n1-0"))
                .hasMessageContaining("games_movetext_no_result_token");
    }

    @Test
    void acceptsALineBreakBetweenMovesBecausePgnWrapsLongGames() throws SQLException {
        assertThat(insertWith("movetext", "1. e4 e5\n2. Nf3 Nc6")).isNotNull();
    }

    @Test
    void acceptsMovetextWhoseLastMoveMerelyContainsADash() throws SQLException {
        assertThat(insertWith("movetext", "1. e4 e5 2. Nf3 Nc6 3. O-O-O")).isNotNull();
    }

    /**
     * PostgreSQL sorts NULLs first under a descending sort, which would place every
     * undated game ahead of the most recent dated one. The indexes pin NULLS LAST so
     * the most-recent-first queries can be served by them directly rather than
     * falling back to a sort.
     */
    @Test
    void ordersUndatedGamesLastInEveryRecencyIndex() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT indexname, indexdef FROM pg_indexes"
                             + " WHERE tablename = 'games' AND indexdef LIKE '%played_on%'")) {
            try (var rows = statement.executeQuery()) {
                int indexes = 0;
                while (rows.next()) {
                    indexes++;
                    assertThat(rows.getString("indexdef"))
                            .as("index %s", rows.getString("indexname"))
                            .contains("played_on DESC NULLS LAST");
                }
                assertThat(indexes).isEqualTo(3);
            }
        }
    }

    @Test
    void rejectsAnUntrimmedOptionalTag() {
        assertThatThrownBy(() -> insertWith("event", " Club Championship "))
                .hasMessageContaining("games_event");
    }

    @Test
    void rejectsThePgnUnknownMarkerAsAnOptionalTagBecauseUnknownIsNull() {
        assertThatThrownBy(() -> insertWith("site", "?"))
                .hasMessageContaining("games_site");
    }
}
