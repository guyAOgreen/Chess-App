package com.chessapp.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class PlayerSchemaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DataSource dataSource;

    private void insert(String displayName, String fideId, String federation) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO players (display_name, fide_id, federation) VALUES (?, ?, ?)")) {
            statement.setString(1, displayName);
            statement.setString(2, fideId);
            statement.setString(3, federation);
            statement.executeUpdate();
        }
    }

    private UUID idOf(String displayName) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT id FROM players WHERE display_name = ?")) {
            statement.setString(1, displayName);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    @Test
    void assignsATimeOrderedUuidWithoutTheCallerSupplyingOne() throws SQLException {
        insert("Carlsen, Magnus", "1503014", "NOR");

        UUID id = idOf("Carlsen, Magnus");

        assertThat(id).isNotNull();
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void acceptsAPlayerWithNoFideIdAndNoFederation() throws SQLException {
        insert("Club Opponent", null, null);

        assertThat(idOf("Club Opponent")).isNotNull();
    }

    @Test
    void rejectsADuplicateDisplayName() throws SQLException {
        insert("Green, Guy", null, "ENG");

        assertThatThrownBy(() -> insert("Green, Guy", null, "RSA"))
                .hasMessageContaining("players_display_name_idx");
    }

    @Test
    void rejectsADuplicateFideId() throws SQLException {
        insert("Smith, John", "2000123", null);

        assertThatThrownBy(() -> insert("Smith, J.", "2000123", null))
                .hasMessageContaining("players_fide_id_idx");
    }

    @Test
    void allowsManyPlayersWithNoFideId() throws SQLException {
        insert("Nameless One", null, null);
        insert("Nameless Two", null, null);

        assertThat(idOf("Nameless Two")).isNotNull();
    }

    @Test
    void rejectsABlankDisplayName() {
        assertThatThrownBy(() -> insert("   ", null, null))
                .hasMessageContaining("players_display_name");
    }

    @Test
    void rejectsThePgnUnknownMarkerAsADisplayName() {
        assertThatThrownBy(() -> insert("?", null, null))
                .hasMessageContaining("players_display_name_not_unknown");
    }

    @Test
    void rejectsAnUntrimmedDisplayNameSoThePaddedUnknownMarkerCannotSlipThrough() {
        assertThatThrownBy(() -> insert(" ? ", null, null))
                .hasMessageContaining("players_display_name_trimmed");
    }

    @Test
    void rejectsANonNumericFideId() {
        assertThatThrownBy(() -> insert("Bad Fide", "12a34", null))
                .hasMessageContaining("players_fide_id_digits");
    }

    @Test
    void rejectsAFederationThatIsNotThreeUppercaseLetters() {
        assertThatThrownBy(() -> insert("Bad Federation", null, "eng"))
                .hasMessageContaining("players_federation_format");
    }
}
