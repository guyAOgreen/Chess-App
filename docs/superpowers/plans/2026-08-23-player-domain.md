# Player Domain Model and Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce `Player` as the first domain module in the core service — a real-world chess player, resolvable by name during PGN import — with its Flyway migration and tests.

**Architecture:** A pure domain record in `domain/`, a JPA entity confined to `persistence/`, and a repository interface declared in the domain and implemented by a persistence adapter. Creation goes through a PostgreSQL `INSERT ... ON CONFLICT DO NOTHING` upsert followed by a read, so a concurrent import cannot produce a duplicate and no exception is used as control flow.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Data JPA, Flyway, PostgreSQL 18, JUnit 5, AssertJ, Testcontainers.

**Spec:** [`docs/superpowers/specs/2026-08-22-player-domain-design.md`](../specs/2026-08-22-player-domain-design.md)

## Global Constraints

- **Java 25.** The pom sets `<java.version>25</java.version>`. `JAVA_HOME` must point at a JDK 25 install (`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`). On JDK 21 the build fails with `error: release version 25 not supported`.
- **Docker must be running.** Every `*IT` starts a `postgres:18` container through Testcontainers.
- **Test naming decides the runner.** `*Test` runs under surefire (`mvn test`), `*IT` under failsafe (`mvn verify`). Follow the existing `ApplicationContextIT`.
- **No Spring or JPA in `domain/`.** No `org.springframework.*` and no `jakarta.persistence.*` imports in that package. This is the convention every later module copies.
- **`spring.jpa.hibernate.ddl-auto` is `validate`.** The entity mapping and the migration must agree exactly or the Spring context fails to start.
- **PostgreSQL 18 only.** `uuidv7()` and `ON CONFLICT` are both PostgreSQL-specific and deliberately so.
- **`READ COMMITTED` isolation is required**, and is the default. Under `REPEATABLE READ` the read after the upsert would not see a concurrently committed row.
- **Working directory for all Maven commands:** `services/core`.
- **Every task ends with a commit.** No task leaves the build red.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `src/main/resources/db/migration/V1__create_players.sql` | The `players` table, its constraints and indexes |
| `src/main/java/com/chessapp/player/domain/PlayerValues.java` | Package-private validation and normalisation, shared by both records |
| `src/main/java/com/chessapp/player/domain/NewPlayer.java` | Validated creation values, no id |
| `src/main/java/com/chessapp/player/domain/Player.java` | A persisted player; always has an id |
| `src/main/java/com/chessapp/player/domain/PlayerIdentityConflict.java` | Unchecked; contradictory identity data |
| `src/main/java/com/chessapp/player/domain/PlayerRepository.java` | Repository interface in domain terms |
| `src/main/java/com/chessapp/player/persistence/PlayerEntity.java` | JPA mapping, read path only |
| `src/main/java/com/chessapp/player/persistence/PlayerJpaRepository.java` | Spring Data, plus the native upsert |
| `src/main/java/com/chessapp/player/persistence/PlayerRepositoryAdapter.java` | Implements `PlayerRepository`; maps entity to domain |
| `src/main/java/com/chessapp/player/application/FindOrCreatePlayer.java` | The use case #7 consumes |
| `src/test/java/com/chessapp/player/domain/PlayerTest.java` | Validation rules, no database |
| `src/test/java/com/chessapp/player/persistence/PlayerSchemaIT.java` | The migration's constraints and indexes, tested through plain JDBC |
| `src/test/java/com/chessapp/player/persistence/PlayerRepositoryIT.java` | Upsert behaviour and entity-to-domain mapping |
| `src/test/java/com/chessapp/player/application/FindOrCreatePlayerIT.java` | Use case, concurrency, identity conflict |

**Deviation from the spec's test table:** the spec put the CHECK-constraint tests
inside `PlayerRepositoryIT`. They are split into `PlayerSchemaIT` here so the
migration can be written test-first, before any Java exists to hold a repository.
The coverage is the same; only the class boundary differs.

**Why `PlayerEntity` has no `@GeneratedValue`:** creation happens through the native upsert, never through `save()`. Hibernate only ever reads this entity, so it needs no identifier generator and never has to populate a generated id on flush. Do not add `save()` usage without revisiting the spec's Risks section.

---

## Task 1: Confirm the build runs on JDK 25

Closes [#28](https://github.com/guyAOgreen/Chess-App/issues/28). No production code. Do this first: until it passes, no test in this plan can be run at all.

This was verified on 2026-08-23 while writing the plan — Temurin 25.0.4.101 was
installed and `mvn clean verify` reported `BUILD SUCCESS` with
`Starting ApplicationContextIT using Java 25.0.4.1`. The steps below are therefore a
confirmation on the executing machine rather than an open question. Before that, a
clean build failed with `error: release version 25 not supported` on JDK 21.

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: nothing
- Produces: a working `mvn verify`, which every later task depends on

- [ ] **Step 1: Point JAVA_HOME at JDK 25 and confirm the version**

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
"$JAVA_HOME/bin/java" -version
```

Expected: `openjdk version "25...`. If the directory does not exist, install it first:
`winget install --id EclipseAdoptium.Temurin.25.JDK -e --silent --accept-package-agreements`

- [ ] **Step 2: Confirm Docker is running**

```bash
docker info --format '{{.ServerVersion}}'
```

Expected: a version number. If it fails, start Docker Desktop and wait for the daemon.

- [ ] **Step 3: Run the full build from clean**

Run, in `services/core`:

```bash
mvn clean verify
```

Expected: `BUILD SUCCESS`, with `ApplicationContextIT` passing. This is the evidence #28 asks for: the backend compiles at release 25 and its integration test passes against a real PostgreSQL 18.

- [ ] **Step 4: Record the requirement in the README**

In `README.md`, under `## Prerequisites`, replace the line `- JDK 25` with:

```markdown
- JDK 25 — `JAVA_HOME` must point at it. The pom targets release 25, so an
  older JDK fails with `error: release version 25 not supported`.
```

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "Verify the backend build on JDK 25

mvn clean verify passes at release 25 with ApplicationContextIT running
against a PostgreSQL 18 container. Records the JAVA_HOME requirement,
because an older JDK fails with a release-version error rather than
anything that points at the cause.

Closes #28"
```

---

## Task 2: The players table

The migration lands before any Java, so later tasks have a schema to validate against. Constraints are tested directly here — they are rules the database must enforce no matter which code path writes.

**Files:**
- Create: `src/main/resources/db/migration/V1__create_players.sql`
- Create: `src/test/java/com/chessapp/player/persistence/PlayerSchemaIT.java`

**Interfaces:**
- Consumes: nothing
- Produces: table `players` with columns `id, display_name, fide_id, federation, created_at, updated_at`; unique indexes `players_display_name_key` and `players_fide_id_key`; check constraints `players_display_name_trimmed`, `players_display_name_not_blank`, `players_display_name_not_unknown`, `players_fide_id_digits`, `players_federation_format`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/chessapp/player/persistence/PlayerSchemaIT.java`:

```java
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
                .hasMessageContaining("players_display_name_key");
    }

    @Test
    void rejectsADuplicateFideId() throws SQLException {
        insert("Smith, John", "2000123", null);

        assertThatThrownBy(() -> insert("Smith, J.", "2000123", null))
                .hasMessageContaining("players_fide_id_key");
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
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
mvn verify -Dit.test=PlayerSchemaIT
```

Expected: FAIL — `relation "players" does not exist`.

- [ ] **Step 3: Write the migration**

Create `src/main/resources/db/migration/V1__create_players.sql`:

```sql
-- A real-world chess player, who need not be an application user.
--
-- display_name is the identity key: PGN import matches on it exactly, after
-- trimming. The trimmed constraint keeps database uniqueness aligned with that
-- rule, and stops a padded ' ? ' from slipping past the unknown-marker check.
CREATE TABLE players (
    id           UUID        PRIMARY KEY DEFAULT uuidv7(),
    display_name TEXT        NOT NULL,
    fide_id      TEXT        NULL,
    federation   TEXT        NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT players_display_name_trimmed     CHECK (display_name = btrim(display_name)),
    CONSTRAINT players_display_name_not_blank   CHECK (btrim(display_name) <> ''),
    CONSTRAINT players_display_name_not_unknown CHECK (display_name <> '?'),
    CONSTRAINT players_fide_id_digits           CHECK (fide_id IS NULL OR fide_id ~ '^[0-9]+$'),
    CONSTRAINT players_federation_format        CHECK (federation IS NULL OR federation ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX players_display_name_key ON players (display_name);

-- Partial: most players have no FIDE ID, and NULLs must not collide.
CREATE UNIQUE INDEX players_fide_id_key ON players (fide_id) WHERE fide_id IS NOT NULL;
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn verify -Dit.test=PlayerSchemaIT
```

Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V1__create_players.sql \
        src/test/java/com/chessapp/player/persistence/PlayerSchemaIT.java
git commit -m "Add the players table

First Flyway migration. display_name is unique because import resolves
players by exact name; fide_id is unique only where present, since most
players have none.

The trimmed-name constraint exists so database uniqueness matches the
domain's after-trimming identity rule, and so a padded ' ? ' cannot get
past the unknown-marker check by a direct write."
```

---

## Task 3: The domain records

Pure Java, no Spring, no database, no container. These tests run under surefire in milliseconds.

**Files:**
- Create: `src/main/java/com/chessapp/player/domain/PlayerValues.java`
- Create: `src/main/java/com/chessapp/player/domain/NewPlayer.java`
- Create: `src/main/java/com/chessapp/player/domain/Player.java`
- Create: `src/test/java/com/chessapp/player/domain/PlayerTest.java`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `public record NewPlayer(String displayName, String fideId, String federation)`
  - `public record Player(UUID id, String displayName, String fideId, String federation)`
  - Both throw `IllegalArgumentException` on invalid input and store trimmed values.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/chessapp/player/domain/PlayerTest.java`:

```java
package com.chessapp.player.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlayerTest {

    private static final UUID ID = UUID.fromString("019535d9-3df7-79fb-b466-fa907fa17f9e");

    @Nested
    class Validation {

        @Test
        void storesTheTrimmedDisplayName() {
            assertThat(new NewPlayer("  Green, Guy  ", null, null).displayName())
                    .isEqualTo("Green, Guy");
        }

        @Test
        void rejectsANullDisplayName() {
            assertThatThrownBy(() -> new NewPlayer(null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("displayName");
        }

        @Test
        void rejectsABlankDisplayName() {
            assertThatThrownBy(() -> new NewPlayer("   ", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void rejectsThePgnUnknownMarker() {
            assertThatThrownBy(() -> new NewPlayer("?", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        void rejectsThePgnUnknownMarkerEvenWhenPadded() {
            assertThatThrownBy(() -> new NewPlayer("  ?  ", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        void acceptsAnAbsentFideIdAndFederation() {
            NewPlayer player = new NewPlayer("Club Opponent", null, null);

            assertThat(player.fideId()).isNull();
            assertThat(player.federation()).isNull();
        }

        @Test
        void acceptsANumericFideId() {
            assertThat(new NewPlayer("Carlsen, Magnus", "1503014", null).fideId())
                    .isEqualTo("1503014");
        }

        @Test
        void rejectsANonNumericFideId() {
            assertThatThrownBy(() -> new NewPlayer("Bad", "12a34", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fideId");
        }

        @Test
        void acceptsAThreeLetterUppercaseFederation() {
            assertThat(new NewPlayer("Green, Guy", null, "ENG").federation()).isEqualTo("ENG");
        }

        @Test
        void rejectsALowercaseFederation() {
            assertThatThrownBy(() -> new NewPlayer("Green, Guy", null, "eng"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("federation");
        }

        @Test
        void rejectsAFederationOfTheWrongLength() {
            assertThatThrownBy(() -> new NewPlayer("Green, Guy", null, "ENGL"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("federation");
        }
    }

    @Nested
    class PersistedPlayer {

        @Test
        void requiresAnId() {
            assertThatThrownBy(() -> new Player(null, "Green, Guy", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }

        @Test
        void appliesTheSameRulesWhenRehydratingAStoredRow() {
            assertThatThrownBy(() -> new Player(ID, "?", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        void holdsTheValidatedValues() {
            Player player = new Player(ID, "Carlsen, Magnus", "1503014", "NOR");

            assertThat(player.id()).isEqualTo(ID);
            assertThat(player.displayName()).isEqualTo("Carlsen, Magnus");
            assertThat(player.fideId()).isEqualTo("1503014");
            assertThat(player.federation()).isEqualTo("NOR");
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
mvn test -Dtest=PlayerTest
```

Expected: FAIL — compilation error, `NewPlayer` and `Player` do not exist.

- [ ] **Step 3: Write the three source files**

Create `src/main/java/com/chessapp/player/domain/PlayerValues.java`:

```java
package com.chessapp.player.domain;

import java.util.regex.Pattern;

/**
 * Validation and normalisation shared by {@link NewPlayer} and {@link Player},
 * so a value created before persistence and the same value rehydrated afterwards
 * cannot acquire different rules.
 */
final class PlayerValues {

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern FEDERATION = Pattern.compile("[A-Z]{3}");

    /** The PGN marker for an unknown tag value. Never a real player. */
    private static final String PGN_UNKNOWN = "?";

    private PlayerValues() {
    }

    static String displayName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("displayName is required");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (PGN_UNKNOWN.equals(trimmed)) {
            throw new IllegalArgumentException(
                    "displayName must not be \"?\", the PGN unknown player marker");
        }
        return trimmed;
    }

    static String fideId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!DIGITS.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("fideId must contain digits only, was: " + raw);
        }
        return trimmed;
    }

    static String federation(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!FEDERATION.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "federation must be three uppercase letters, was: " + raw);
        }
        return trimmed;
    }
}
```

Create `src/main/java/com/chessapp/player/domain/NewPlayer.java`:

```java
package com.chessapp.player.domain;

/**
 * A validated request to create a player. Deliberately not a {@link Player}:
 * it has no identity, because identity is assigned by the database.
 */
public record NewPlayer(String displayName, String fideId, String federation) {

    public NewPlayer {
        displayName = PlayerValues.displayName(displayName);
        fideId = PlayerValues.fideId(fideId);
        federation = PlayerValues.federation(federation);
    }
}
```

Create `src/main/java/com/chessapp/player/domain/Player.java`:

```java
package com.chessapp.player.domain;

import java.util.UUID;

/**
 * A real-world chess player, who need not be an application user.
 *
 * <p>A {@code Player} always exists in the database, so {@code id} is never null.
 * Values are validated again on construction, so a corrupt or unexpectedly shaped
 * row cannot enter the domain unnoticed.
 */
public record Player(UUID id, String displayName, String fideId, String federation) {

    public Player {
        if (id == null) {
            throw new IllegalArgumentException("id is required; a Player is always persisted");
        }
        displayName = PlayerValues.displayName(displayName);
        fideId = PlayerValues.fideId(fideId);
        federation = PlayerValues.federation(federation);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn test -Dtest=PlayerTest
```

Expected: PASS, 14 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chessapp/player/domain/ \
        src/test/java/com/chessapp/player/domain/PlayerTest.java
git commit -m "Add the Player and NewPlayer domain records

NewPlayer carries validated creation values without an identity, so the
domain never holds a Player with a null id and both records can stay
immutable.

Both validate through the same package-private functions, so a value
checked before an insert and the same value rehydrated afterwards cannot
drift apart. No Spring or JPA in this package."
```

---

## Task 4: Persistence adapter and the upsert

Where the concurrency design lives. The entity is mapped for reads only; writes go through the native upsert.

**Files:**
- Create: `src/main/java/com/chessapp/player/domain/PlayerIdentityConflict.java`
- Create: `src/main/java/com/chessapp/player/domain/PlayerRepository.java`
- Create: `src/main/java/com/chessapp/player/persistence/PlayerEntity.java`
- Create: `src/main/java/com/chessapp/player/persistence/PlayerJpaRepository.java`
- Create: `src/main/java/com/chessapp/player/persistence/PlayerRepositoryAdapter.java`
- Create: `src/test/java/com/chessapp/player/persistence/PlayerRepositoryIT.java`

**Interfaces:**
- Consumes: `NewPlayer`, `Player` from Task 3; the `players` table from Task 2
- Produces:
  - `public interface PlayerRepository { Optional<Player> findByDisplayName(String displayName); Player createOrFind(NewPlayer candidate); }`
  - `public class PlayerIdentityConflict extends RuntimeException`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/chessapp/player/persistence/PlayerRepositoryIT.java`:

```java
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
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn verify -Dit.test=PlayerRepositoryIT
```

Expected: FAIL — compilation error, `PlayerRepository` does not exist.

- [ ] **Step 3: Write the domain interface and the conflict type**

Create `src/main/java/com/chessapp/player/domain/PlayerIdentityConflict.java`:

```java
package com.chessapp.player.domain;

/**
 * The supplied identity data contradicts what is stored — for example a new
 * display name carrying a FIDE ID that already belongs to another player.
 *
 * <p>Unchecked, because a caller cannot usefully recover: the input is wrong, not
 * merely unlucky. This is not the concurrent-creation case, which
 * {@link PlayerRepository#createOrFind} handles without failing.
 */
public class PlayerIdentityConflict extends RuntimeException {

    public PlayerIdentityConflict(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Create `src/main/java/com/chessapp/player/domain/PlayerRepository.java`:

```java
package com.chessapp.player.domain;

import java.util.Optional;

/**
 * Declared in the domain, implemented in persistence, so the dependency points
 * inward. There is deliberately no {@code save(Player)}: identity is assigned by
 * the database, so a caller can never hold an unsaved {@link Player}.
 */
public interface PlayerRepository {

    /** Exact, case-sensitive match on the trimmed display name. */
    Optional<Player> findByDisplayName(String displayName);

    /**
     * Returns the existing player with this display name, or creates one.
     *
     * <p>Safe under concurrent callers: two simultaneous calls for the same name
     * return the same player rather than one of them failing.
     *
     * @throws PlayerIdentityConflict if the candidate's FIDE ID already belongs
     *                                to a player with a different display name
     */
    Player createOrFind(NewPlayer candidate);
}
```

- [ ] **Step 4: Write the persistence classes**

Create `src/main/java/com/chessapp/player/persistence/PlayerEntity.java`:

```java
package com.chessapp.player.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Read mapping for the {@code players} table.
 *
 * <p>Deliberately has no {@code @GeneratedValue}: rows are created by the native
 * upsert in {@link PlayerRepositoryAdapter}, never by {@code save()}, so Hibernate
 * is never asked to populate a generated identifier on flush. Introducing
 * {@code save()} for a new entity means revisiting that decision.
 */
@Entity
@Table(name = "players")
class PlayerEntity {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "fide_id")
    private String fideId;

    @Column(name = "federation")
    private String federation;

    protected PlayerEntity() {
        // required by JPA
    }

    UUID getId() {
        return id;
    }

    String getDisplayName() {
        return displayName;
    }

    String getFideId() {
        return fideId;
    }

    String getFederation() {
        return federation;
    }
}
```

Create `src/main/java/com/chessapp/player/persistence/PlayerJpaRepository.java`:

```java
package com.chessapp.player.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlayerJpaRepository extends JpaRepository<PlayerEntity, java.util.UUID> {

    Optional<PlayerEntity> findByDisplayName(String displayName);

    /**
     * Inserts unless the display name is already taken.
     *
     * <p>{@code ON CONFLICT (display_name) DO NOTHING} means a losing concurrent
     * insert neither raises an error nor aborts the transaction, so the caller can
     * simply read afterwards. A {@code fide_id} collision is deliberately not
     * covered by the conflict target: that is contradictory data, not a race, and
     * must surface.
     *
     * @return 1 when this call inserted the row, 0 when it already existed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO players (display_name, fide_id, federation)
            VALUES (:displayName, :fideId, :federation)
            ON CONFLICT (display_name) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("displayName") String displayName,
                       @Param("fideId") String fideId,
                       @Param("federation") String federation);
}
```

Create `src/main/java/com/chessapp/player/persistence/PlayerRepositoryAdapter.java`:

```java
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
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
mvn verify -Dit.test=PlayerRepositoryIT
```

Expected: PASS, 7 tests.

If `aDifferentNameReusingAFideIdIsAnIdentityConflictRatherThanAMatch` fails because the exception is a bare `DataIntegrityViolationException`, the index name is not reaching `mentions`. Print the full cause chain in the test to see the actual text, then match on what is really there — do not weaken the assertion to catch every integrity violation, or a display-name bug would masquerade as an identity conflict.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/chessapp/player/domain/PlayerRepository.java \
        src/main/java/com/chessapp/player/domain/PlayerIdentityConflict.java \
        src/main/java/com/chessapp/player/persistence/ \
        src/test/java/com/chessapp/player/persistence/PlayerRepositoryIT.java
git commit -m "Add the Player repository and its upsert adapter

createOrFind inserts with ON CONFLICT (display_name) DO NOTHING and then
reads. Catching a constraint violation and re-reading would not work: the
violation aborts the transaction, so the re-read fails rather than
returning the winning row.

The conflict target is display_name only. A fide_id collision under a
different name is contradictory data rather than a race, and becomes
PlayerIdentityConflict.

The entity has no @GeneratedValue because nothing calls save(); ids are
assigned by the column default and arrive through the read."
```

---

## Task 5: The FindOrCreatePlayer use case

The application-layer entry point #7 will call, plus the concurrency test that pins the isolation assumption.

**Files:**
- Create: `src/main/java/com/chessapp/player/application/FindOrCreatePlayer.java`
- Create: `src/test/java/com/chessapp/player/application/FindOrCreatePlayerIT.java`

**Interfaces:**
- Consumes: `PlayerRepository`, `NewPlayer`, `Player`, `PlayerIdentityConflict`
- Produces: `public class FindOrCreatePlayer { public Player execute(String displayName, String fideId, String federation) }`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/chessapp/player/application/FindOrCreatePlayerIT.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn verify -Dit.test=FindOrCreatePlayerIT
```

Expected: FAIL — compilation error, `FindOrCreatePlayer` does not exist.

- [ ] **Step 3: Write the use case**

Create `src/main/java/com/chessapp/player/application/FindOrCreatePlayer.java`:

```java
package com.chessapp.player.application;

import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.Player;
import com.chessapp.player.domain.PlayerRepository;
import org.springframework.stereotype.Service;

/**
 * Resolves a player name, as it appears in a PGN tag, to a stored {@link Player}.
 *
 * <p>Consumed by PGN import (#7). Constructing the {@link NewPlayer} validates and
 * normalises before the database is touched, so invalid input fails with a domain
 * error rather than a constraint violation.
 */
@Service
public class FindOrCreatePlayer {

    private final PlayerRepository players;

    public FindOrCreatePlayer(PlayerRepository players) {
        this.players = players;
    }

    public Player execute(String displayName, String fideId, String federation) {
        return players.createOrFind(new NewPlayer(displayName, fideId, federation));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
mvn verify -Dit.test=FindOrCreatePlayerIT
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Run the whole build**

```bash
mvn clean verify
```

Expected: `BUILD SUCCESS`. All unit tests and all four integration tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/chessapp/player/application/ \
        src/test/java/com/chessapp/player/application/FindOrCreatePlayerIT.java
git commit -m "Add the FindOrCreatePlayer use case

The entry point PGN import will call to resolve a player name into a row.
Validation happens by constructing NewPlayer, so bad input fails as a
domain error before the database is involved.

The concurrency test runs two callers on separate connections, released
together, and asserts both resolve to one identity. It is what pins the
READ COMMITTED assumption the adapter depends on."
```

---

## Task 6: Close the issue

**Files:**
- Modify: none

- [ ] **Step 1: Confirm the domain package has no framework imports**

```bash
grep -rE "^import (org\.springframework|jakarta\.persistence)" \
     src/main/java/com/chessapp/player/domain/ && echo "LEAK FOUND" || echo "clean"
```

Expected: `clean`. If anything is found, move it to `persistence/` or `application/` before continuing — this is the convention every later module copies.

- [ ] **Step 2: Confirm the full build is green from scratch**

```bash
mvn clean verify
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Push and open the pull request**

```bash
git push -u origin feat/player-domain
```

Then open a PR against `master` titled `Player domain model and persistence`, closing #4 and #28, summarising: the domain/entity separation and why, the upsert approach and why catch-and-re-read would not work, the identity and unknown-player decisions, and the two known limitations from the spec.

---

## Notes for the implementer

**If `mvn verify` fails before you have written anything**, check `JAVA_HOME` first — see Global Constraints. A release-version error names the JDK, not your code.

**Run one integration test at a time while iterating.** `-Dit.test=Name` limits failsafe to a single class. Each class starts its own container, so a full `mvn verify` is slower than it looks.

**Do not add `save()` calls to `PlayerJpaRepository`.** The entity has no identifier generator on purpose. Adding one reintroduces a question the spec deliberately closed.

**Do not weaken the identity-conflict assertion** to catch any `DataIntegrityViolationException`. It would pass for the wrong reason and hide a display-name bug.
