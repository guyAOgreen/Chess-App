# PGN Import Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accept a PGN document at `POST /api/games`, validate it deterministically, resolve its players, persist the game, and return the created resource.

**Architecture:** A `GameController` in `game/api` calls an `ImportPgn` use case in `game/application`. `ImportPgn` returns a sealed `PgnImportResult` — `Imported(Game)` or `Rejected(PgnError)` — so an invalid document is a return value the controller cannot forget to handle, never an exception. The chess work already exists: `PgnParser` validates, `FindOrCreatePlayer` resolves names, `GameRepository.save` persists. This adds orchestration and an HTTP contract, nothing more.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring MVC, Bean Validation, Maven, JUnit 5, AssertJ, MockMvc, Testcontainers (PostgreSQL 18).

**Spec:** [docs/superpowers/specs/2026-08-25-pgn-import-endpoint-design.md](../specs/2026-08-25-pgn-import-endpoint-design.md)

## Global Constraints

- **Java 25, Spring Boot 4.1.0.** No new dependencies: `spring-boot-starter-web` and `spring-boot-starter-validation` are already in `services/core/pom.xml`.
- **The API is mounted at `/api`, declared literally.** Controllers write `@RequestMapping("/api/games")`. No `WebMvcConfigurer` path prefix. (Spec decision 10.)
- **Errors are RFC 9457 problem details**, `application/problem+json`. `code` is always present; `ply` is present only when the parser supplied one, and is **omitted** rather than sent as null. (Spec decision 2.)
- **422 for an unusable game; 400 for an unusable request.** All ten `PgnErrorCode` values map to 422. (Spec decision 4.)
- **`ImportPgn` declares no `@Transactional`.** Both adapters manage their own boundaries, and `createOrFind` is `REQUIRES_NEW`, so an outer boundary would imply an atomicity that does not exist. (Spec decision 6.)
- **`PlayerIdentityConflict` is not mapped.** It cannot be reached from an endpoint that supplies no FIDE ID. Do not add a handler for it. (Spec decision 7.)
- **`GameResponse` excludes `sourcePgn`** and any assembled canonical PGN. (Spec decision 11.)
- **`GameSide.name` comes from the parsed document**, not from `Player.displayName()`. (Spec decision 8.)
- **`pgn` is capped at 1,048,576 UTF-16 code units.** (Spec decision 9.)
- **Persistence entities never appear in responses.** `GameEntity` stays inside `game/persistence`.
- **Style:** 4-space indent, ~100 column limit, javadoc that explains *why* rather than restating *what*. Match the surrounding code.

### Commands

```bash
# Unit tests only (fast, no Docker)
mvn -f services/core/pom.xml test -Dtest=ClassName

# Integration tests (*IT, run by failsafe) — REQUIRES DOCKER RUNNING
mvn -f services/core/pom.xml verify -Dit.test=ClassName -DfailIfNoTests=false

# Everything
mvn -f services/core/pom.xml verify
```

Integration tests use Testcontainers and will fail with a connection error if Docker is not running. That is an environment problem, not a code problem.

Tests share one PostgreSQL container per class with no cleanup between methods, so **every test must use player names unique to that test**. Reusing a name across tests couples them through the `players` table.

---

## File Structure

**Create:**

| File | Responsibility |
| --- | --- |
| `services/core/src/main/java/com/chessapp/game/application/PgnImportResult.java` | Sealed outcome of an import: `Imported` or `Rejected` |
| `services/core/src/main/java/com/chessapp/game/application/ImportPgn.java` | The use case: parse, resolve players, save |
| `services/core/src/main/java/com/chessapp/game/api/ImportPgnRequest.java` | Request body: `{ "pgn": ... }`, with the size cap |
| `services/core/src/main/java/com/chessapp/game/api/GameResponse.java` | The game detail representation, built from domain `Game` |
| `services/core/src/main/java/com/chessapp/game/api/GameController.java` | `POST /api/games`: call, switch, respond |
| `services/core/src/test/java/com/chessapp/game/application/ImportPgnIT.java` | Orchestration, without HTTP |
| `services/core/src/test/java/com/chessapp/game/api/GameApiIT.java` | The HTTP contract, end to end |

**Modify:**

| File | Change |
| --- | --- |
| `services/core/src/main/resources/application.yml` | Add `spring.mvc.problemdetails.enabled: true` |

Nothing is added to a `shared/` package. There is no global `@RestControllerAdvice`: rejection handling lives in the controller, and the only candidate exception is deliberately unmapped.

---

## Task 1: The import use case

Orchestration with no HTTP involved. Ends with a working import that a test can drive directly.

**Files:**
- Create: `services/core/src/main/java/com/chessapp/game/application/PgnImportResult.java`
- Create: `services/core/src/main/java/com/chessapp/game/application/ImportPgn.java`
- Test: `services/core/src/test/java/com/chessapp/game/application/ImportPgnIT.java`

**Interfaces:**
- Consumes: `PgnParser.parse(String) → PgnParseResult` (`Parsed(ParsedGame game)` | `Rejected(PgnError error)`); `ParsedGame(String event, String site, LocalDate playedOn, String round, String whiteName, String blackName, Integer whiteRating, Integer blackRating, String eco, GameResult result, String movetext)`; `PgnError(PgnErrorCode code, String message, Integer ply)`; `FindOrCreatePlayer.execute(String displayName, String fideId, String federation) → Player`; `GameRepository.save(NewGame) → Game`; `NewGame(GameSide white, GameSide black, String event, String site, String round, LocalDate playedOn, GameResult result, String eco, GameSource source, String movetext, String sourcePgn)`; `GameSide(UUID playerId, String name, Integer rating)`.
- Produces: `ImportPgn.execute(String pgn) → PgnImportResult`; `PgnImportResult.Imported(Game game)`; `PgnImportResult.Rejected(PgnError error)`. Task 2 and Task 3 consume all of these.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/com/chessapp/game/application/ImportPgnIT.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -f services/core/pom.xml verify -Dit.test=ImportPgnIT -DfailIfNoTests=false`

Expected: FAIL — compilation error, `ImportPgn` and `PgnImportResult` do not exist.

- [ ] **Step 3: Write the sealed result**

Create `services/core/src/main/java/com/chessapp/game/application/PgnImportResult.java`:

```java
package com.chessapp.game.application;

import com.chessapp.chess.PgnError;
import com.chessapp.game.domain.Game;

/**
 * The outcome of importing a PGN document.
 *
 * <p>Sealed for the same reason {@code PgnParseResult} is: an invalid document is
 * an expected outcome for an endpoint fed by users, not an exceptional condition,
 * and a sealed type makes the failure impossible to forget where it is consumed.
 * That consumer is the controller, which is why the rejection travels this far as
 * a value rather than being re-wrapped as an exception at the layer boundary.
 */
public sealed interface PgnImportResult {

    record Imported(Game game) implements PgnImportResult {
    }

    record Rejected(PgnError error) implements PgnImportResult {
    }
}
```

- [ ] **Step 4: Write the use case**

Create `services/core/src/main/java/com/chessapp/game/application/ImportPgn.java`:

```java
package com.chessapp.game.application;

import com.chessapp.chess.ParsedGame;
import com.chessapp.chess.PgnParseResult;
import com.chessapp.chess.PgnParser;
import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GameRepository;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.NewGame;
import com.chessapp.player.application.FindOrCreatePlayer;
import com.chessapp.player.domain.Player;
import org.springframework.stereotype.Service;

/**
 * Turns a submitted PGN document into a stored {@link Game}.
 *
 * <p>Deliberately not {@code @Transactional}. {@code PlayerRepository.createOrFind}
 * is {@code REQUIRES_NEW} and commits independently of any caller, and
 * {@code GameRepository.save} is a single insert that is atomic on its own — so an
 * outer boundary would wrap nothing the adapters do not already cover, while
 * implying an atomicity that {@code REQUIRES_NEW} explicitly breaks. A reader
 * would reasonably infer that a failed insert rolls the players back. It does not.
 *
 * <p>Revisit if this ever writes more than one row of its own.
 */
@Service
public class ImportPgn {

    private final PgnParser parser;
    private final FindOrCreatePlayer findOrCreatePlayer;
    private final GameRepository games;

    public ImportPgn(PgnParser parser, FindOrCreatePlayer findOrCreatePlayer,
            GameRepository games) {
        this.parser = parser;
        this.findOrCreatePlayer = findOrCreatePlayer;
        this.games = games;
    }

    /** Never throws for bad input: an unusable document comes back as a rejection. */
    public PgnImportResult execute(String pgn) {
        // Parsing first means an invalid document never reaches the database, and
        // the common failure costs no connection.
        return switch (parser.parse(pgn)) {
            case PgnParseResult.Rejected rejected ->
                    new PgnImportResult.Rejected(rejected.error());
            case PgnParseResult.Parsed parsed -> store(parsed.game(), pgn);
        };
    }

    /**
     * The names written onto the game are the document's, not the resolved
     * players'. They are the same string today, because matching is exact on the
     * trimmed name, but they mean different things: {@code GameSide.name} is a
     * game-time snapshot, and taking it from the resolved player would start
     * rewriting history the day aliasing makes matching non-exact.
     *
     * <p>{@code sourcePgn} is the submitted value unchanged. ADR 0002 makes it
     * provenance that nothing reads to answer a product question, so normalising
     * it would defeat the point of keeping it.
     */
    private PgnImportResult store(ParsedGame parsed, String sourcePgn) {
        Player white = findOrCreatePlayer.execute(parsed.whiteName(), null, null);
        Player black = findOrCreatePlayer.execute(parsed.blackName(), null, null);
        Game game = games.save(new NewGame(
                new GameSide(white.id(), parsed.whiteName(), parsed.whiteRating()),
                new GameSide(black.id(), parsed.blackName(), parsed.blackRating()),
                parsed.event(),
                parsed.site(),
                parsed.round(),
                parsed.playedOn(),
                parsed.result(),
                parsed.eco(),
                GameSource.PGN_IMPORT,
                parsed.movetext(),
                sourcePgn));
        return new PgnImportResult.Imported(game);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -f services/core/pom.xml verify -Dit.test=ImportPgnIT -DfailIfNoTests=false`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/application/ \
        services/core/src/test/java/com/chessapp/game/application/ImportPgnIT.java
git commit -m "Import a PGN document into a stored game"
```

- [ ] **Step 7: Add the remaining orchestration tests**

Append these methods to `ImportPgnIT`. One new import is needed: `java.util.UUID`.

```java
    @Test
    void persistsTheGameSoItCanBeReadBack() {
        Game game = imported(pgn("Readback White", "Readback Black"));

        Game found = games.findById(game.id()).orElseThrow();

        assertThat(found).isEqualTo(game);
    }

    @Test
    void createsBothPlayers() {
        imported(pgn("Created White", "Created Black"));

        assertThat(players.findByDisplayName("Created White")).isPresent();
        assertThat(players.findByDisplayName("Created Black")).isPresent();
    }

    @Test
    void reusesAPlayerAlreadyStoredRatherThanCreatingASecond() {
        UUID first = imported(pgn("Repeat White", "Repeat Black One")).white().playerId();

        UUID second = imported(pgn("Repeat White", "Repeat Black Two")).white().playerId();

        assertThat(second).isEqualTo(first);
    }

    /**
     * Legal, if unusual, PGN. One player row is resolved for both colours rather
     * than the import failing.
     */
    @Test
    void storesAGameWhoseTwoColoursNameTheSamePlayer() {
        Game game = imported(pgn("Self Opponent", "Self Opponent"));

        assertThat(game.white().playerId()).isEqualTo(game.black().playerId());
    }

    /**
     * {@code sourcePgn} is provenance, so it is the submitted value unchanged — a
     * byte order mark included. It is the deserialised string, not the original
     * HTTP bytes: at this layer there is no encoding left to preserve.
     */
    @Test
    void keepsTheSubmittedDocumentUnchangedAsProvenance() {
        String submitted = "﻿" + pgn("Provenance White", "Provenance Black");

        Game game = imported(submitted);

        assertThat(game.sourcePgn()).isEqualTo(submitted);
        assertThat(game.movetext())
                .as("movetext is canonical, not a copy of what was submitted")
                .isEqualTo("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
    }

    @Test
    void returnsTheParserRejectionRatherThanThrowing() {
        String illegal = """
                [White "Illegal White"]
                [Black "Illegal Black"]
                [Result "*"]

                1. e4 e5 2. Nf3 Nc6 3. e6 *
                """;

        PgnImportResult result = importPgn.execute(illegal);

        assertThat(result).isInstanceOfSatisfying(PgnImportResult.Rejected.class, rejected -> {
            assertThat(rejected.error().code()).isEqualTo(PgnErrorCode.ILLEGAL_MOVE);
            assertThat(rejected.error().ply()).isEqualTo(5);
        });
    }

    @Test
    void leavesTheDatabaseUntouchedWhenTheDocumentIsRejected() {
        String illegal = """
                [White "Untouched White"]
                [Black "Untouched Black"]
                [Result "*"]

                1. e4 e5 2. Nf3 Nc6 3. e6 *
                """;

        importPgn.execute(illegal);

        assertThat(players.findByDisplayName("Untouched White")).isEmpty();
        assertThat(players.findByDisplayName("Untouched Black")).isEmpty();
    }
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `mvn -f services/core/pom.xml verify -Dit.test=ImportPgnIT -DfailIfNoTests=false`

Expected: PASS, 8 tests.

- [ ] **Step 9: Commit**

```bash
git add services/core/src/test/java/com/chessapp/game/application/ImportPgnIT.java
git commit -m "Cover player reuse, provenance and rejection in the import use case"
```

---

## Task 2: The created resource

The 201 path over HTTP. Error mapping is Task 3; this task only proves a valid document becomes a resource.

**Files:**
- Create: `services/core/src/main/java/com/chessapp/game/api/ImportPgnRequest.java`
- Create: `services/core/src/main/java/com/chessapp/game/api/GameResponse.java`
- Create: `services/core/src/main/java/com/chessapp/game/api/GameController.java`
- Modify: `services/core/src/main/resources/application.yml`
- Test: `services/core/src/test/java/com/chessapp/game/api/GameApiIT.java`

**Interfaces:**
- Consumes: `ImportPgn.execute(String) → PgnImportResult`; `PgnImportResult.Imported(Game game)`; `PgnImportResult.Rejected(PgnError error)` from Task 1.
- Produces: `GameResponse.from(Game) → GameResponse` with nested `GameResponse.Side(UUID playerId, String name, Integer rating)`; `ImportPgnRequest(String pgn)`; `GameController.importGame(ImportPgnRequest) → ResponseEntity<Object>` mapped to `POST /api/games`. #8 and #9 reuse `GameResponse`.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/com/chessapp/game/api/GameApiIT.java`:

```java
package com.chessapp.game.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.game.domain.GameRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GameApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameRepository games;

    /**
     * The container is shared across the class with no cleanup between methods, so
     * each test names its own players.
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

    /**
     * Built with Jackson rather than by hand: a PGN document is full of newlines
     * and quotation marks, and hand-escaping them into a JSON literal is a source
     * of test bugs that look like production bugs.
     */
    private ResultActions importing(String pgn) throws Exception {
        return mockMvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pgn", pgn))));
    }

    @Test
    void answersCreatedWithTheStoredGame() throws Exception {
        importing(pgn("Api White", "Api Black"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/games/")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.white.name").value("Api White"))
                .andExpect(jsonPath("$.white.playerId").isNotEmpty())
                .andExpect(jsonPath("$.white.rating").value(1850))
                .andExpect(jsonPath("$.black.name").value("Api Black"))
                .andExpect(jsonPath("$.black.rating").doesNotExist())
                .andExpect(jsonPath("$.event").value("Club Championship"))
                .andExpect(jsonPath("$.site").value("London ENG"))
                .andExpect(jsonPath("$.round").value("3.2"))
                .andExpect(jsonPath("$.playedOn").value("2026-03-14"))
                .andExpect(jsonPath("$.result").value("WHITE_WON"))
                .andExpect(jsonPath("$.eco").value("C60"))
                .andExpect(jsonPath("$.source").value("PGN_IMPORT"))
                .andExpect(jsonPath("$.movetext").value("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6"));
    }

    /**
     * A 201 that reported an id for a row that was never written would satisfy
     * every assertion above, so the identifier is followed back to the database.
     */
    @Test
    void persistsTheGameItReports() throws Exception {
        String body = importing(pgn("Persisted White", "Persisted Black"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        assertThat(games.findById(id)).isPresent();
    }

    /**
     * ADR 0002 makes source_pgn provenance that nothing reads to answer a product
     * question. Putting it in the resource representation would contradict that,
     * and would ship the moves twice.
     */
    @Test
    void doesNotExposeTheSubmittedDocument() throws Exception {
        importing(pgn("Hidden White", "Hidden Black"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourcePgn").doesNotExist())
                .andExpect(jsonPath("$.pgn").doesNotExist());
    }
}
```

Note on `jsonPath("$.black.rating").doesNotExist()`: an absent rating serialises as
JSON `null`, and JsonPath treats a null value as absent. Both `doesNotExist()` and
`value(nullValue())` pass; `doesNotExist()` is used consistently here.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -f services/core/pom.xml verify -Dit.test=GameApiIT -DfailIfNoTests=false`

Expected: FAIL — compilation error, `GameResponse` and the controller do not exist.

- [ ] **Step 3: Write the request body**

Create `services/core/src/main/java/com/chessapp/game/api/ImportPgnRequest.java`:

```java
package com.chessapp.game.api;

import jakarta.validation.constraints.Size;

/**
 * The submitted document, and nothing else. {@code source} is fixed server-side to
 * {@code PGN_IMPORT}, which is true by construction of anything arriving here; a
 * client-declared provenance would be a field to defend and nothing yet needs it.
 *
 * <p>An absent or null {@code pgn} is deliberately not rejected here. The parser
 * already answers "no PGN text was supplied" as {@code NOT_PGN}, and a
 * {@code @NotBlank} would add a second code path reaching the same conclusion in a
 * different format with a different status.
 *
 * <p>The cap is 1,048,576 UTF-16 code units, not bytes: Bean Validation measures
 * {@code String.length()}. It is an application-level limit that bounds the work
 * the parser can be asked to do — Jackson has already deserialised the body by the
 * time validation runs, so it does NOT cap bytes received. A transport limit needs
 * a reverse proxy or a servlet filter, and is tracked as a deployment
 * prerequisite.
 */
public record ImportPgnRequest(@Size(max = 1_048_576) String pgn) {
}
```

- [ ] **Step 4: Write the response body**

Create `services/core/src/main/java/com/chessapp/game/api/GameResponse.java`:

```java
package com.chessapp.game.api;

import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The game detail representation. #9 returns the same shape for a single game and
 * #8 reuses it for list rows, so this is the shared contract rather than one
 * endpoint's output.
 *
 * <p>Built from the domain {@link Game}, so {@code GameEntity} never leaves
 * persistence.
 *
 * <p>Carries neither {@code sourcePgn} nor an assembled canonical document. The
 * viewer re-parses {@code movetext} to drive the board, so shipping the assembled
 * PGN would send the same moves twice in every response; and {@code sourcePgn} is
 * provenance rather than part of the resource. Export as a PGN file is a distinct
 * representation, decided when something needs it.
 *
 * <p>Optional metadata is present as null rather than omitted, so a client sees
 * one shape whatever the document said.
 */
public record GameResponse(UUID id,
                           Side white,
                           Side black,
                           String event,
                           String site,
                           String round,
                           LocalDate playedOn,
                           GameResult result,
                           String eco,
                           GameSource source,
                           String movetext) {

    /** One colour's share of the game. {@code name} is the game-time snapshot. */
    public record Side(UUID playerId, String name, Integer rating) {
    }

    public static GameResponse from(Game game) {
        return new GameResponse(game.id(),
                side(game.white()),
                side(game.black()),
                game.event(),
                game.site(),
                game.round(),
                game.playedOn(),
                game.result(),
                game.eco(),
                game.source(),
                game.movetext());
    }

    private static Side side(GameSide side) {
        return new Side(side.playerId(), side.name(), side.rating());
    }
}
```

- [ ] **Step 5: Write the controller, success path only**

Create `services/core/src/main/java/com/chessapp/game/api/GameController.java`:

```java
package com.chessapp.game.api;

import com.chessapp.game.application.ImportPgn;
import com.chessapp.game.application.PgnImportResult;
import com.chessapp.game.domain.Game;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The path is written out rather than applied by a {@code WebMvcConfigurer} prefix:
 * the vite dev server proxies {@code /api} without rewriting, so the backend must
 * serve {@code /api/games}, and a reader searching the repository for that path
 * should find it.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final ImportPgn importPgn;

    public GameController(ImportPgn importPgn) {
        this.importPgn = importPgn;
    }

    @PostMapping
    public ResponseEntity<Object> importGame(@Valid @RequestBody ImportPgnRequest request) {
        return switch (importPgn.execute(request.pgn())) {
            case PgnImportResult.Imported imported -> created(imported.game());
            case PgnImportResult.Rejected ignored ->
                    throw new UnsupportedOperationException("rejection mapping: task 3");
        };
    }

    /**
     * A relative URI built from the created identifier rather than from
     * {@code ServletUriComponentsBuilder}. There is no proxy configuration that
     * would make an absolute URI correct, and a relative one cannot be wrong.
     */
    private static ResponseEntity<Object> created(Game game) {
        return ResponseEntity.created(URI.create("/api/games/" + game.id()))
                .body(GameResponse.from(game));
    }
}
```

The `UnsupportedOperationException` is scaffolding that Task 3 replaces. It is deliberately loud: no test in this task reaches it, and if one did the failure would be obvious rather than silent.

- [ ] **Step 6: Turn on problem details**

Modify `services/core/src/main/resources/application.yml`. Add an `mvc` block under the existing `spring:` key, immediately after `application:` and before `datasource:`:

```yaml
spring:
  application:
    name: chess-app-core
  mvc:
    problemdetails:
      # RFC 9457 problem details, which default to OFF in Boot 4.1. Without this,
      # Spring's own errors — malformed JSON, wrong method, wrong content type —
      # return the legacy {"timestamp","error","path"} body while our rejections
      # return problem+json. Two error shapes on one API is worse than either.
      enabled: true
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5433/chessapp}
```

Leave the rest of the file unchanged.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn -f services/core/pom.xml verify -Dit.test=GameApiIT -DfailIfNoTests=false`

Expected: PASS, 3 tests.

- [ ] **Step 8: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/api/ \
        services/core/src/main/resources/application.yml \
        services/core/src/test/java/com/chessapp/game/api/GameApiIT.java
git commit -m "Create a game from a submitted PGN document"
```

---

## Task 3: The rejection contract

Replaces the Task 2 scaffolding with the real mapping. This is the part clients branch on, so it is a task of its own.

**Files:**
- Modify: `services/core/src/main/java/com/chessapp/game/api/GameController.java`
- Test: `services/core/src/test/java/com/chessapp/game/api/GameApiIT.java`

**Interfaces:**
- Consumes: `PgnImportResult.Rejected(PgnError error)`; `PgnError(PgnErrorCode code, String message, Integer ply)`.
- Produces: a 422 `application/problem+json` body with `type`, `title`, `status`, `detail`, `code`, and `ply` when non-null. #8, #9, #14 and #18 follow this shape.

- [ ] **Step 1: Write the failing tests**

Append to `GameApiIT`. No new imports are needed — Task 2 added them all.

```java
    /**
     * The fixture and its ply are taken from ChesslibPgnParserTest, where the same
     * document is already pinned to ILLEGAL_MOVE at ply 5 — e4 cannot reach e6.
     */
    @Test
    void answersUnprocessableContentWithTheCodeAndPlyForAnIllegalMove() throws Exception {
        String illegal = """
                [White "Reject Illegal White"]
                [Black "Reject Illegal Black"]
                [Result "*"]

                1. e4 e5 2. Nf3 Nc6 3. e6 *
                """;

        importing(illegal)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/errors/invalid-pgn"))
                .andExpect(jsonPath("$.title").value("Invalid PGN"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.code").value("ILLEGAL_MOVE"))
                .andExpect(jsonPath("$.ply").value(5));
    }

    /**
     * A rejection that is not about a specific move omits ply rather than sending
     * null, so a client can branch on presence.
     */
    @Test
    void omitsPlyWhenTheProblemIsNotAboutAMove() throws Exception {
        String noMoves = """
                [White "Reject Moveless White"]
                [Black "Reject Moveless Black"]
                [Result "*"]

                *
                """;

        importing(noMoves)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_MOVES"))
                .andExpect(jsonPath("$.ply").doesNotExist());
    }

    @Test
    void rejectsADocumentThatNamesNoPlayer() throws Exception {
        String unknown = """
                [White "?"]
                [Black "Reject Unknown Black"]
                [Result "1-0"]

                1. e4 e5 1-0
                """;

        importing(unknown)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PLAYER_UNKNOWN"));
    }

    @Test
    void rejectsAFileHoldingMoreThanOneGame() throws Exception {
        String two = """
                [White "Reject Multi White"]
                [Black "Reject Multi Black"]
                [Result "1-0"]

                1. e4 e5 1-0

                [White "Reject Multi White Two"]
                [Black "Reject Multi Black Two"]
                [Result "0-1"]

                1. d4 d5 0-1
                """;

        importing(two)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MULTIPLE_GAMES"));
    }

    @Test
    void rejectsAGameThatDeclaresNoResult() throws Exception {
        String none = """
                [White "Reject Resultless White"]
                [Black "Reject Resultless Black"]

                1. e4 e5
                """;

        importing(none)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESULT_MISSING"));
    }

    @Test
    void treatsAnAbsentPgnFieldAsAnEmptyDocument() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_PGN"));
    }

    /**
     * U+2028 is a line terminator that Java's \\R matches but
     * Character.isISOControl does not, so PgnTagValues lets it through where
     * GameValues would reject it. It cannot reach domain construction: the tag is
     * split across two lines, matches the tag pattern on neither, and the orphaned
     * fragments land in the movetext section where chesslib fails on them.
     *
     * <p>The assertion is that this is a 422 and not a 500 — that the document
     * never reaches NewGame. Verified empirically against ChesslibPgnParser before
     * this test was written; the first version of the analysis had the mechanism
     * wrong while reaching the right conclusion, which is why it is pinned here.
     */
    @Test
    void rejectsALineSeparatorInATagRatherThanFailingInsideTheDomain() throws Exception {
        String separator = """
                [Event "Club%sChampionship"]
                [White "Reject Separator White"]
                [Black "Reject Separator Black"]
                [Result "1-0"]

                1. e4 e5 1-0
                """.formatted(Character.toString(0x2028));

        importing(separator)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_PGN"));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -f services/core/pom.xml verify -Dit.test=GameApiIT -DfailIfNoTests=false`

Expected: FAIL — the seven new tests fail with `UnsupportedOperationException: rejection mapping: task 3`, surfacing as HTTP 500.

- [ ] **Step 3: Replace the scaffolding with the mapping**

In `services/core/src/main/java/com/chessapp/game/api/GameController.java`, replace the `throw new UnsupportedOperationException(...)` arm and add the `invalidPgn` method. Add these imports: `com.chessapp.chess.PgnError`, `org.springframework.http.HttpStatus`, `org.springframework.http.MediaType`, `org.springframework.http.ProblemDetail`.

The switch arm becomes:

```java
            case PgnImportResult.Rejected rejected -> invalidPgn(rejected.error());
```

Declare the type constant with the class's other fields, above `importPgn`:

```java
    /** RFC 9457 type for a document that cannot become a game. Relative by design. */
    private static final URI INVALID_PGN = URI.create("/errors/invalid-pgn");
```

And add this method alongside `created`:

```java
    /**
     * Every PgnErrorCode is a 422: the request was understood and the content was
     * the problem. The code says which, so splitting the status would give clients
     * two things to branch on instead of one.
     *
     * <p>The content type is set explicitly rather than relying on Spring to infer
     * it from the body type, so the wire format is stated where it is decided.
     *
     * <p>ply is set only when present, so a client can branch on the field's
     * presence rather than on a null.
     */
    private static ResponseEntity<Object> invalidPgn(PgnError error) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setType(INVALID_PGN);
        problem.setTitle("Invalid PGN");
        problem.setDetail(error.message());
        problem.setProperty("code", error.code().name());
        if (error.ply() != null) {
            problem.setProperty("ply", error.ply());
        }
        return ResponseEntity.unprocessableEntity()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -f services/core/pom.xml verify -Dit.test=GameApiIT -DfailIfNoTests=false`

Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/api/GameController.java \
        services/core/src/test/java/com/chessapp/game/api/GameApiIT.java
git commit -m "Answer an unusable PGN with a problem detail a client can act on"
```

---

## Task 4: Requests the endpoint will not read

The 400s: a body that is not readable JSON, and a document past the cap. Also pins that Spring's own errors now use the same body shape as ours, which is the point of the `application.yml` change.

**Files:**
- Test: `services/core/src/test/java/com/chessapp/game/api/GameApiIT.java`

**Interfaces:**
- Consumes: everything from Tasks 2 and 3. No production code changes are expected — the behaviour comes from `@Valid`, `@Size` and the problem-details setting already in place.

- [ ] **Step 1: Write the failing tests**

Append to `GameApiIT`:

```java
    /**
     * The assertion that spring.mvc.problemdetails.enabled took effect. Without it
     * this body is the legacy {"timestamp","error","path"} shape, and a client
     * written against problem+json would mis-handle it.
     */
    @Test
    void answersBadRequestInProblemJsonForABodyThatIsNotJson() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pgn\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void answersBadRequestForAnEmptyBody() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * One code unit past the cap. This bounds the work the parser can be asked to
     * do; it does not bound bytes received, because Jackson has already
     * deserialised the body by the time validation runs.
     */
    @Test
    void answersBadRequestForADocumentPastTheSizeCap() throws Exception {
        importing("x".repeat(1_048_577))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void answersUnsupportedMediaTypeForANonJsonContentType() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(pgn("Unsupported White", "Unsupported Black")))
                .andExpect(status().isUnsupportedMediaType());
    }
```

- [ ] **Step 2: Run the tests**

Run: `mvn -f services/core/pom.xml verify -Dit.test=GameApiIT -DfailIfNoTests=false`

Expected: PASS, 14 tests — these assert behaviour that Tasks 2 and 3 already put in place, so no new production code should be needed.

If `answersBadRequestForADocumentPastTheSizeCap` returns **422 instead of 400**, `@Valid` is not being applied: confirm `@Valid` is present on the `@RequestBody` parameter in `GameController`. Do not "fix" this by moving the cap into `ImportPgn` — the cap is a property of the request, not of the document.

If either problem+json assertion fails with the legacy body shape, the `application.yml` change from Task 2 Step 6 did not land. Check the indentation: `mvc` must be nested under `spring`, not at the document root.

- [ ] **Step 3: Commit**

```bash
git add services/core/src/test/java/com/chessapp/game/api/GameApiIT.java
git commit -m "Pin the responses for requests the endpoint will not read"
```

- [ ] **Step 4: Run the whole suite**

Run: `mvn -f services/core/pom.xml verify`

Expected: PASS. Every pre-existing test still passes; `ApplicationContextIT` in particular proves the new beans do not break context startup.

- [ ] **Step 5: Verify the endpoint by hand**

Start the infrastructure and the application:

```bash
docker compose -f infra/docker-compose.yml up -d
mvn -f services/core/pom.xml spring-boot:run
```

In another shell:

```bash
curl -i -X POST http://localhost:8080/api/games \
  -H 'Content-Type: application/json' \
  -d '{"pgn": "[Event \"Manual Check\"]\n[White \"Green, Guy\"]\n[Black \"Manual Opponent\"]\n[Result \"1-0\"]\n\n1. e4 e5 2. Nf3 Nc6 1-0\n"}'
```

Expected: `201 Created`, a `Location: /api/games/<uuid>` header, and the game as JSON.

```bash
curl -i -X POST http://localhost:8080/api/games \
  -H 'Content-Type: application/json' \
  -d '{"pgn": "[White \"A\"]\n[Black \"B\"]\n[Result \"*\"]\n\n1. e4 e5 2. Nf3 Nc6 3. e6 *\n"}'
```

Expected: `422`, `Content-Type: application/problem+json`, `"code": "ILLEGAL_MOVE"`, `"ply": 5`.

Stop the application and bring the infrastructure down with `docker compose -f infra/docker-compose.yml down`.

---

## After the plan

Two follow-ups the spec identified. Neither is part of this work; open them as issues so they are tracked rather than rediscovered.

1. **Duplicate detection.** There is no uniqueness constraint on `games`, so the same PGN imported twice stores two rows. Needs a definition of "the same game" and a migration to enforce it race-safely.
2. **A transport-level request limit.** `@Size` bounds parser work but not bytes received, and Spring Boot has no property that closes the gap — `server.max-http-post-size` has been deprecated at error level since 3.0, and `server.tomcat.max-http-form-post-size` is form content only. Needs a reverse proxy limit or a servlet filter, before any publicly reachable deployment and alongside #25.

`CONTEXT.md` needs no update: this implements the API design it already describes and changes no product scope, architecture or domain ownership.
