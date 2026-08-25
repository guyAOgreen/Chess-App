# PGN Parsing and Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn arbitrary PGN text into either a validated game or a clear reason it was rejected, and assemble the canonical PGN document for a stored `Game`.

**Architecture:** A `chess` module reads PGN documents behind our own `PgnParser` interface, with chesslib confined to a single sub-package. Tag values are read by our own tag reader because chesslib's parsed model loses them; move legality is verified by replaying every move against `Board.legalMoves()` because no chesslib path rejects an illegal pawn move. A separate `CanonicalPgn` assembler in the game module writes documents and uses no chess library at all.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Maven, JUnit 5, AssertJ, chesslib 1.3.7 (JitPack), Flyway, PostgreSQL 18, Testcontainers.

**Spec:** [docs/superpowers/specs/2026-08-24-pgn-parsing-design.md](../specs/2026-08-24-pgn-parsing-design.md)

## Global Constraints

- Java 25, Spring Boot 4.1.0, Maven. Module root is `services/core`.
- chesslib is pinned to exactly `com.github.bhlangonijr:chesslib:1.3.7`. Never a branch reference, never `-SNAPSHOT`.
- **No file outside `com.chessapp.chess.chesslib` may import `com.github.bhlangonijr.*`.** This is ADR 0001's core requirement.
- Domain packages (`com.chessapp.game.domain`, `com.chessapp.chess`) stay free of Spring and chesslib imports.
- Unit tests use JUnit 5 with AssertJ (`assertThat`, `assertThatThrownBy`). No Spring context, no Testcontainers, except `GameSchemaIT` which already has both.
- Test naming follows the existing suite: full-sentence method names describing behaviour, `@Nested` classes to group.
- Run a unit test: `mvn -o test -Dtest=ClassName` from `services/core`.
- Run an integration test: `mvn -o verify -Dsurefire.failIfNoSpecifiedTests=false -Dtest=none -Dit.test=ClassName` from `services/core`.
- Run everything: `mvn -o clean verify` from `services/core`.
- The first build after Task 6 must drop `-o` (offline) once, to fetch chesslib from JitPack.
- Commit messages: imperative subject line, body explaining why, and the trailer `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`.
- Every commit must leave `mvn -o clean verify` green.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `docs/adr/0001-java-chess-rules-library.md` | Modify: correct three wrong claims about chesslib |
| `services/core/pom.xml` | Modify: JitPack repository, chesslib dependency |
| `game/domain/GameValues.java` | Modify: reject control characters in tag values |
| `db/migration/V3__reject_control_characters.sql` | Create: matching CHECK constraints |
| `game/domain/CanonicalPgn.java` | Create: `Game` → PGN document |
| `game/domain/GameResult.java` | Modify: add `fromPgnToken` |
| `chess/PgnTagReader.java` | Create: `[Name "value"]` section → map, and the movetext section |
| `chess/PgnTagValues.java` | Create: date, rating, optional-tag and ECO normalisation |
| `chess/PgnParser.java` | Create: the contract |
| `chess/PgnParseResult.java` | Create: sealed `Parsed` \| `Rejected` |
| `chess/ParsedGame.java` | Create: validated chess facts |
| `chess/PgnError.java`, `chess/PgnErrorCode.java` | Create: the failure value |
| `chess/chesslib/ValidatedMoves.java` | Create: SAN → movetext plus terminal state, via `legalMoves()` replay |
| `chess/chesslib/IllegalMoveAtPly.java` | Create: carries the ply our replay stopped at |
| `chess/chesslib/ChesslibPgnParser.java` | Create: orchestration, the only `PgnParser` implementation |

All Java paths are under `services/core/src/main/java/com/chessapp/`, tests under `services/core/src/test/java/com/chessapp/`.

---

### Task 1: Correct ADR 0001

A second probe of chesslib 1.3.7, run while planning this work, contradicted three claims in the ADR. The ADR is the document that justifies the wrapper existing, so a wrong safety claim in it is how someone later deletes the replay loop as redundant.

**Files:**
- Modify: `docs/adr/0001-java-chess-rules-library.md`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing in code. Later tasks cite it.

- [ ] **Step 1: Correct constraint 1**

In the "Constraints the wrapper must enforce" section, replace the closing paragraph of item 1, currently:

```text
   Only two paths are authoritative: `board.legalMoves()`, and SAN parsed through
   `MoveList`. The wrapper must expose only those, and must never accept a
   caller-constructed move without checking it against `legalMoves()`.
```

with:

```text
   **Corrected 2026-08-24 by a second probe.** The original text named SAN parsed
   through `MoveList` as a second authoritative path. It is not.
   `MoveList.loadFromSan("1. e5 e5")` is accepted and yields `1. e5 exe5` — a white
   pawn moving three squares from e2 — and the PGN reader accepts the same input.
   Illegal piece moves and illegal castling *are* rejected; the hole is in pawn
   decoding specifically.

   Only `board.legalMoves()` is authoritative. The wrapper must replay every move
   against it and must never accept a move from any other source without that check.
```

- [ ] **Step 2: Correct constraint 2**

Replace the first sentence of item 2, currently:

```text
2. **Failures are not always `MoveConversionException`.** SAN describing a capture
   of the king — `Qxe1` where e1 holds the enemy king — throws an unchecked
   `ArrayIndexOutOfBoundsException`.
```

with:

```text
2. **Failures are unchecked, and arrive from more than one place.**
   `MoveConversionException`, `PgnException` and `MoveException` all extend
   `RuntimeException`; the original text described the first as a declared checked
   exception, which it is not. **Corrected 2026-08-24:** SAN describing a capture of
   the king surfaces as `PgnException` thrown by the iterator itself, not as
   `ArrayIndexOutOfBoundsException` during move loading, so error handling must wrap
   the iteration. A game with no moves throws `NullPointerException` inside
   `loadMoveText()`.
```

- [ ] **Step 3: Qualify the "what works" claim**

In the "What works" section, replace the bullet beginning `**PGN reading handles real-world files**` — keep its existing text and append this sentence to it:

```text
  **Corrected 2026-08-24:** "handles" means structure, not legality. The reader
  accepts an illegal pawn move; see constraint 1.
```

- [ ] **Step 4: Verify nothing else contradicts the probe**

Run: `grep -n "authoritative\|checked exception\|MoveList" docs/adr/0001-java-chess-rules-library.md`
Expected: every hit is either inside a correction added above, or is unrelated to legality claims. Fix any remaining claim that says a `MoveList` path validates legality.

- [ ] **Step 5: Commit**

```bash
git add docs/adr/0001-java-chess-rules-library.md
git commit -m "$(cat <<'EOF'
Correct ADR 0001 from a second chesslib probe

The ADR named "SAN parsed through MoveList" as an authoritative path for
legality. It is not: loadFromSan accepts a three-square pawn push, and so does
the PGN reader. Only legalMoves() is authoritative, so the wrapper must replay
against it.

Also corrects the exception description. MoveConversionException, PgnException
and MoveException are all unchecked, and the king-capture case surfaces as
PgnException from the iterator rather than ArrayIndexOutOfBoundsException
during move loading.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Reject control characters in values that become PGN tags

Canonical assembly is where this becomes load-bearing: a newline inside a tag value makes the emitted document invalid, because PGN defines a string token as printing characters between quotation marks. Escaping quotes and backslashes does not fix that.

`movetext` is deliberately **not** covered — PGN wraps long games across lines, and `V2` already allows newlines there.

**Files:**
- Modify: `services/core/src/main/java/com/chessapp/game/domain/GameValues.java`
- Create: `services/core/src/main/resources/db/migration/V3__reject_control_characters.sql`
- Test: `services/core/src/test/java/com/chessapp/game/domain/GameSideTest.java`
- Test: `services/core/src/test/java/com/chessapp/game/domain/NewGameTest.java`
- Test: `services/core/src/test/java/com/chessapp/game/persistence/GameSchemaIT.java`

**Interfaces:**
- Consumes: existing `GameValues.playerName(String)` and `GameValues.optionalTag(String)`.
- Produces: both now throw `IllegalArgumentException` when the value contains a control character. Task 3 relies on this.

- [ ] **Step 1: Write the failing domain tests**

Add to `GameSideTest`:

```java
    @Test
    void rejectsANameContainingALineBreakBecauseItWouldBreakPgnAssembly() {
        assertThatThrownBy(() -> new GameSide(PLAYER_ID, "Green,\nGuy", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control character");
    }

    @Test
    void rejectsANameContainingATab() {
        assertThatThrownBy(() -> new GameSide(PLAYER_ID, "Green,\tGuy", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control character");
    }
```

Add to `NewGameTest`, inside the `OptionalTags` nested class:

```java
        @Test
        void rejectsATagContainingALineBreak() {
            assertThatThrownBy(() -> withEvent("Club\nChampionship"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("control character");
        }
```

Add to `NewGameTest`, inside the `Movetext` nested class, pinning that movetext is exempt:

```java
        @Test
        void stillAcceptsALineBreakInMovetextBecausePgnWrapsLongGames() {
            assertThat(withMovetext("1. e4 e5\n2. Nf3 Nc6").movetext())
                    .isEqualTo("1. e4 e5\n2. Nf3 Nc6");
        }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o test -Dtest='GameSideTest,NewGameTest'`
Expected: FAIL — three failures reading "Expecting code to raise a throwable" (the exempt-movetext test passes already, which is correct: it is a guard, not a driver).

- [ ] **Step 3: Add the rule to `GameValues`**

In `GameValues`, add the helper and call it from both methods:

```java
    private static boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
```

In `playerName(String raw)`, after the `PGN_UNKNOWN` check and before `return trimmed;`:

```java
        if (hasControlCharacter(trimmed)) {
            throw new IllegalArgumentException(
                    "name must not contain a control character; it becomes a PGN tag value");
        }
```

In `optionalTag(String raw)`, after the blank-or-unknown check and before `return trimmed;`:

```java
        if (hasControlCharacter(trimmed)) {
            throw new IllegalArgumentException(
                    "tag value must not contain a control character; it becomes a PGN tag value");
        }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -o test -Dtest='GameSideTest,NewGameTest,GameTest'`
Expected: PASS, all green.

- [ ] **Step 5: Write the failing schema test**

Add to `GameSchemaIT`:

```java
    @Test
    void rejectsAGameTimeNameContainingALineBreak() {
        assertThatThrownBy(() -> insertWith("white_name", "Green,\nGuy"))
                .hasMessageContaining("games_white_name_no_control");
    }

    @Test
    void rejectsAnOptionalTagContainingALineBreak() {
        assertThatThrownBy(() -> insertWith("event", "Club\nChampionship"))
                .hasMessageContaining("games_event_no_control");
    }

    @Test
    void stillAcceptsALineBreakInMovetextBecausePgnWrapsLongGames() throws SQLException {
        assertThat(insertWith("movetext", "1. e4 e5\n2. Nf3 Nc6")).isNotNull();
    }
```

- [ ] **Step 6: Run the schema test to verify it fails**

Run: `mvn -o verify -Dsurefire.failIfNoSpecifiedTests=false -Dtest=none -Dit.test=GameSchemaIT`
Expected: FAIL — the two rejection tests fail because the inserts succeed; no constraint exists yet.

- [ ] **Step 7: Write the migration**

Create `services/core/src/main/resources/db/migration/V3__reject_control_characters.sql`:

```sql
-- Values that become PGN tag values must not contain control characters.
--
-- PGN defines a string token as printing characters between quotation marks, so an
-- embedded newline makes the emitted document invalid whatever the escaping, and
-- line-oriented readers mis-parse it. Escaping quotes and backslashes prevents a
-- value forging a tag; it does not make a newline legal.
--
-- The btrim idiom used in V2 cannot express this: PostgreSQL's single-argument
-- btrim strips spaces only, while Java's trim() strips every character up to and
-- including the space, so the two disagree on tabs and newlines.
--
-- movetext is deliberately exempt. PGN wraps long games across lines, and V2
-- already accepts a line break between moves.
ALTER TABLE games
    ADD CONSTRAINT games_white_name_no_control CHECK (white_name !~ '[[:cntrl:]]'),
    ADD CONSTRAINT games_black_name_no_control CHECK (black_name !~ '[[:cntrl:]]'),
    ADD CONSTRAINT games_event_no_control CHECK (event IS NULL OR event !~ '[[:cntrl:]]'),
    ADD CONSTRAINT games_site_no_control  CHECK (site  IS NULL OR site  !~ '[[:cntrl:]]'),
    ADD CONSTRAINT games_round_no_control CHECK (round IS NULL OR round !~ '[[:cntrl:]]');

-- eco needs no constraint: games_eco_format already requires ^[A-E][0-9]{2}$.
```

- [ ] **Step 8: Run the schema test to verify it passes**

Run: `mvn -o verify -Dsurefire.failIfNoSpecifiedTests=false -Dtest=none -Dit.test=GameSchemaIT`
Expected: PASS, all green.

- [ ] **Step 9: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/domain/GameValues.java \
        services/core/src/main/resources/db/migration/V3__reject_control_characters.sql \
        services/core/src/test/java/com/chessapp/game/domain/GameSideTest.java \
        services/core/src/test/java/com/chessapp/game/domain/NewGameTest.java \
        services/core/src/test/java/com/chessapp/game/persistence/GameSchemaIT.java
git commit -m "$(cat <<'EOF'
Reject control characters in values that become PGN tags

PGN defines a string token as printing characters between quotation marks, so a
newline inside a tag value makes the emitted document invalid however it is
escaped. Canonical assembly is about to depend on that, so the rule lands now.

Covers game-time names and the optional Event, Site and Round tags, in the
domain and in a matching CHECK constraint. movetext stays exempt: PGN wraps
long games across lines.

The constraints cannot reuse V2's btrim idiom, because PostgreSQL's
single-argument btrim strips spaces only while Java's trim() strips everything
up to the space.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Canonical PGN assembler

`Game` in, PGN document out. No chess library: ADR 0002 established that assembly is pure string work over metadata plus `movetext`, and ADR 0001 recorded that chesslib's own `toPgn()` injects tags that were never in the input.

**Files:**
- Create: `services/core/src/main/java/com/chessapp/game/domain/CanonicalPgn.java`
- Test: `services/core/src/test/java/com/chessapp/game/domain/CanonicalPgnTest.java`

**Interfaces:**
- Consumes: `Game`, `GameSide`, `GameResult.pgnToken()`, and Task 2's control-character guarantee.
- Produces: `public static String CanonicalPgn.from(Game game)`. Task 8 uses it.

- [ ] **Step 1: Write the failing test**

Create `CanonicalPgnTest`:

```java
package com.chessapp.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalPgnTest {

    private static final UUID ID = UUID.fromString("019535d9-5b22-7f04-8e15-3c9a7d2f6b81");
    private static final UUID WHITE_ID = UUID.fromString("019535d9-3df7-79fb-b466-fa907fa17f9e");
    private static final UUID BLACK_ID = UUID.fromString("019535d9-4aa1-7c2e-9d31-2b6f1c4e8a70");

    private static Game fullyPopulated() {
        return new Game(ID,
                new GameSide(WHITE_ID, "Green, Guy", 1850),
                new GameSide(BLACK_ID, "Club Opponent", 1720),
                "Club Championship", "London ENG", "3.2", LocalDate.of(2026, 3, 14),
                GameResult.WHITE_WON, "C60", GameSource.PGN_IMPORT,
                "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6", null);
    }

    private static Game bare() {
        return new Game(ID,
                new GameSide(WHITE_ID, "Green, Guy", null),
                new GameSide(BLACK_ID, "Club Opponent", null),
                null, null, null, null, GameResult.DRAW, null, GameSource.PERSONAL,
                "1. d4 d5", null);
    }

    @Test
    void emitsTheSevenTagRosterInSpecificationOrderThenTheOptionalTags() {
        assertThat(CanonicalPgn.from(fullyPopulated())).isEqualTo("""
                [Event "Club Championship"]
                [Site "London ENG"]
                [Date "2026.03.14"]
                [Round "3.2"]
                [White "Green, Guy"]
                [Black "Club Opponent"]
                [Result "1-0"]
                [WhiteElo "1850"]
                [BlackElo "1720"]
                [ECO "C60"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """);
    }

    @Test
    void writesUnknownStringsAsQuestionMarksAndAnUnknownDateAsTheMaskedForm() {
        assertThat(CanonicalPgn.from(bare())).isEqualTo("""
                [Event "?"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "Green, Guy"]
                [Black "Club Opponent"]
                [Result "1/2-1/2"]

                1. d4 d5 1/2-1/2
                """);
    }

    @Test
    void escapesQuotesAndBackslashesInTagValues() {
        Game game = new Game(ID,
                new GameSide(WHITE_ID, "O'Brien, \"Bobby\"", null),
                new GameSide(BLACK_ID, "Back\\slash", null),
                null, null, null, null, GameResult.BLACK_WON, null, GameSource.PERSONAL,
                "1. e4 e5", null);

        assertThat(CanonicalPgn.from(game))
                .contains("[White \"O'Brien, \\\"Bobby\\\"\"]")
                .contains("[Black \"Back\\\\slash\"]");
    }

    @Test
    void usesLineFeedsAndEndsWithExactlyOneNewlineOnEveryPlatform() {
        String pgn = CanonicalPgn.from(bare());

        assertThat(pgn).doesNotContain("\r");
        assertThat(pgn).endsWith("1. d4 d5 1/2-1/2\n");
        assertThat(pgn).doesNotEndWith("\n\n");
    }

    @Test
    void appendsTheResultTokenForEveryResult() {
        for (GameResult result : GameResult.values()) {
            Game game = new Game(ID,
                    new GameSide(WHITE_ID, "A", null), new GameSide(BLACK_ID, "B", null),
                    null, null, null, null, result, null, GameSource.PERSONAL, "1. e4 e5", null);

            assertThat(CanonicalPgn.from(game))
                    .as("result %s", result)
                    .contains("[Result \"" + result.pgnToken() + "\"]")
                    .endsWith("1. e4 e5 " + result.pgnToken() + "\n");
        }
    }

    @Test
    void omitsRatingsAndEcoWhenTheyAreNotKnown() {
        assertThat(CanonicalPgn.from(bare()))
                .doesNotContain("WhiteElo")
                .doesNotContain("BlackElo")
                .doesNotContain("ECO");
    }

    @Test
    void injectsNoneOfTheTagsChesslibWouldAdd() {
        assertThat(CanonicalPgn.from(fullyPopulated()))
                .doesNotContain("PlyCount")
                .doesNotContain("TimeControl")
                .doesNotContain("Annotator");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o test -Dtest=CanonicalPgnTest`
Expected: FAIL — compilation error, `cannot find symbol: class CanonicalPgn`.

- [ ] **Step 3: Write the assembler**

Create `services/core/src/main/java/com/chessapp/game/domain/CanonicalPgn.java`:

```java
package com.chessapp.game.domain;

import java.time.format.DateTimeFormatter;

/**
 * Assembles the canonical PGN document for a {@link Game}.
 *
 * <p>ADR 0002 makes the moves canonical and the metadata relational, so the tag
 * pair section has no stored form that could go stale: it is a function of the
 * current metadata, computed on demand.
 *
 * <p>No chess library is involved. ADR 0001 records that chesslib's own
 * {@code toPgn()} injects tags that were never in the input, reorders the tag pair
 * section and mangles comment spacing.
 */
public final class CanonicalPgn {

    private static final DateTimeFormatter PGN_DATE = DateTimeFormatter.ofPattern("uuuu.MM.dd");

    /** The PGN convention for a tag value that is not known. */
    private static final String UNKNOWN = "?";

    /** The PGN convention for a date that is not known. */
    private static final String UNKNOWN_DATE = "????.??.??";

    private CanonicalPgn() {
    }

    public static String from(Game game) {
        if (game == null) {
            throw new IllegalArgumentException("game is required");
        }
        StringBuilder pgn = new StringBuilder();
        tag(pgn, "Event", game.event());
        tag(pgn, "Site", game.site());
        tag(pgn, "Date", game.playedOn() == null ? UNKNOWN_DATE : PGN_DATE.format(game.playedOn()));
        tag(pgn, "Round", game.round());
        tag(pgn, "White", game.white().name());
        tag(pgn, "Black", game.black().name());
        tag(pgn, "Result", game.result().pgnToken());
        optionalTag(pgn, "WhiteElo", game.white().rating());
        optionalTag(pgn, "BlackElo", game.black().rating());
        if (game.eco() != null) {
            tag(pgn, "ECO", game.eco());
        }
        pgn.append('\n');
        pgn.append(game.movetext()).append(' ').append(game.result().pgnToken()).append('\n');
        return pgn.toString();
    }

    private static void optionalTag(StringBuilder pgn, String name, Integer value) {
        if (value != null) {
            tag(pgn, name, String.valueOf(value));
        }
    }

    private static void tag(StringBuilder pgn, String name, String value) {
        pgn.append('[').append(name).append(" \"")
                .append(escape(value == null ? UNKNOWN : value))
                .append("\"]\n");
    }

    /**
     * Backslash first: escaping quotes first would then double-escape the
     * backslashes it introduced.
     *
     * <p>Control characters need no escape because they cannot be here — the domain
     * rejects them, since PGN string tokens are printing characters and no escape
     * makes an embedded newline legal.
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o test -Dtest=CanonicalPgnTest`
Expected: PASS, all green.

- [ ] **Step 5: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/domain/CanonicalPgn.java \
        services/core/src/test/java/com/chessapp/game/domain/CanonicalPgnTest.java
git commit -m "$(cat <<'EOF'
Assemble canonical PGN from a Game

ADR 0002 makes the tag pair section a function of the current metadata rather
than a stored column, so it cannot drift from the values it describes. This is
that function: Seven Tag Roster in specification order, then ratings and ECO
when known, then movetext with the result token from the authoritative column.

Tag values are escaped per the PGN string rules, backslash before quote so the
escaping does not double itself. Line endings are fixed to \n so identical
games produce identical bytes on every platform.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Read the tag pair section ourselves

chesslib's parsed model loses `Round` — both `"3.2"` and `"?"` arrive as the integer `1`, and neither appears in its property map. ADR 0002 typed the column `TEXT` precisely because those values are legal, so the tags are read from the document instead.

**Files:**
- Create: `services/core/src/main/java/com/chessapp/chess/PgnTagReader.java`
- Test: `services/core/src/test/java/com/chessapp/chess/PgnTagReaderTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `public static Map<String, String> PgnTagReader.tags(String pgn)` and `public static String PgnTagReader.movetext(String pgn)`. Task 7 uses both. They are `public` rather than package-private because `ChesslibPgnParser` lives in the `chesslib` sub-package and could not otherwise reach them.

- [ ] **Step 1: Write the failing test**

Create `PgnTagReaderTest`:

```java
package com.chessapp.chess;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgnTagReaderTest {

    private static final String GAME = """
            [Event "Club Championship"]
            [Site "London ENG"]
            [Round "3.2"]
            [White "Green, Guy"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 Nc6 1-0
            """;

    @Test
    void readsEveryTagPairIntoTheMap() {
        assertThat(PgnTagReader.tags(GAME))
                .containsEntry("Event", "Club Championship")
                .containsEntry("Site", "London ENG")
                .containsEntry("White", "Green, Guy")
                .containsEntry("Result", "1-0");
    }

    @Test
    void keepsTheRoundTagVerbatimBecauseChesslibTurnsItIntoAnInteger() {
        assertThat(PgnTagReader.tags(GAME)).containsEntry("Round", "3.2");
    }

    @Test
    void keepsTheUnknownMarkerVerbatimRatherThanTreatingItAsAbsent() {
        assertThat(PgnTagReader.tags("[Round \"?\"]\n\n1. e4 *\n"))
                .containsEntry("Round", "?");
    }

    @Test
    void unescapesQuotesAndBackslashesInValues() {
        assertThat(PgnTagReader.tags("[White \"O'Brien, \\\"Bobby\\\"\"]\n"))
                .containsEntry("White", "O'Brien, \"Bobby\"");
        assertThat(PgnTagReader.tags("[Site \"Back\\\\slash\"]\n"))
                .containsEntry("Site", "Back\\slash");
    }

    @Test
    void acceptsIrregularWhitespaceAroundATagPair() {
        assertThat(PgnTagReader.tags("   [  Event   \"Spaced\"  ]   \n"))
                .containsEntry("Event", "Spaced");
    }

    @Test
    void returnsAnEmptyMapForTextWithNoTagPairs() {
        assertThat(PgnTagReader.tags("1. e4 e5 *")).isEmpty();
    }

    @Test
    void returnsAnEmptyMapForNull() {
        assertThat(PgnTagReader.tags(null)).isEmpty();
    }

    @Test
    void takesTheFirstValueWhenATagIsRepeated() {
        assertThat(PgnTagReader.tags("[Event \"First\"]\n[Event \"Second\"]\n"))
                .containsEntry("Event", "First");
    }

    @Test
    void returnsEverythingOutsideTheTagPairSectionAsMovetext() {
        assertThat(PgnTagReader.movetext(GAME)).isEqualTo("1. e4 e5 2. Nf3 Nc6 1-0");
    }

    @Test
    void keepsLineBreaksWithinMovetextBecausePgnWrapsLongGames() {
        String wrapped = """
                [Event "?"]

                1. e4 e5
                2. Nf3 Nc6
                """;

        assertThat(PgnTagReader.movetext(wrapped)).isEqualTo("1. e4 e5\n2. Nf3 Nc6");
    }

    @Test
    void returnsEmptyMovetextForAGameWithNoMoves() {
        assertThat(PgnTagReader.movetext("[Event \"?\"]\n[Result \"*\"]\n")).isEmpty();
    }

    @Test
    void returnsEmptyMovetextForNull() {
        assertThat(PgnTagReader.movetext(null)).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o test -Dtest=PgnTagReaderTest`
Expected: FAIL — compilation error, `cannot find symbol: class PgnTagReader`.

- [ ] **Step 3: Write the reader**

Create `services/core/src/main/java/com/chessapp/chess/PgnTagReader.java`:

```java
package com.chessapp.chess;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the tag pair section of a PGN document.
 *
 * <p>This exists because chesslib's parsed model is lossy for our metadata.
 * {@code [Round "3.2"]} and {@code [Round "?"]} both arrive as the integer 1, and
 * {@code Round} appears in no property map, while ADR 0002 typed the column
 * {@code TEXT} precisely because those values are legal. A missing {@code Result}
 * is also indistinguishable from {@code *} in that model.
 *
 * <p>No chesslib import: this class reads text, and owning it makes metadata
 * fidelity our responsibility rather than a property of the library we chose.
 */
public final class PgnTagReader {

    private static final Pattern TAG_PAIR = Pattern.compile(
            "^\\s*\\[\\s*([A-Za-z0-9_]+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"\\s*]\\s*$");

    private PgnTagReader() {
    }

    /**
     * Every tag pair in the document, in the order they appear. A repeated tag keeps
     * its first value, so the first game's tags win in a document holding several —
     * which the parser rejects anyway, having counted them first.
     */
    public static Map<String, String> tags(String pgn) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (pgn == null) {
            return tags;
        }
        for (String line : pgn.split("\\R", -1)) {
            Matcher tagPair = TAG_PAIR.matcher(line);
            if (tagPair.matches()) {
                tags.putIfAbsent(tagPair.group(1), unescape(tagPair.group(2)));
            }
        }
        return tags;
    }

    /**
     * Everything that is not a tag pair, trimmed. Interior line breaks are kept:
     * PGN wraps long games across lines, and the moves either side of the break are
     * a single sequence.
     */
    public static String movetext(String pgn) {
        if (pgn == null) {
            return "";
        }
        StringBuilder movetext = new StringBuilder();
        for (String line : pgn.split("\\R", -1)) {
            if (!TAG_PAIR.matcher(line).matches()) {
                movetext.append(line).append('\n');
            }
        }
        return movetext.toString().trim();
    }

    /**
     * One pass, so an escaped backslash cannot have its output re-read as the start
     * of another escape.
     */
    private static String unescape(String value) {
        StringBuilder unescaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' && i + 1 < value.length()) {
                unescaped.append(value.charAt(++i));
            } else {
                unescaped.append(current);
            }
        }
        return unescaped.toString();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o test -Dtest=PgnTagReaderTest`
Expected: PASS, all green.

- [ ] **Step 5: Commit**

```bash
git add services/core/src/main/java/com/chessapp/chess/PgnTagReader.java \
        services/core/src/test/java/com/chessapp/chess/PgnTagReaderTest.java
git commit -m "$(cat <<'EOF'
Read PGN tag pairs ourselves

chesslib's parsed model cannot carry our metadata. Round "3.2" and Round "?"
both arrive as the integer 1 and appear in no property map, defeating ADR
0002's deliberate choice of a TEXT column, and a missing Result tag is
indistinguishable from "*".

Reading the tag pair section ourselves makes metadata fidelity our
responsibility rather than a property of the library we happened to choose, and
stops us discovering lossy mappings one at a time.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Normalise tag values

Interpreting `2026.??.??`, `?` and a non-numeric rating is knowledge about PGN documents rather than about our `Game`, so it belongs with the reader. #7 then receives values already in our types.

**Files:**
- Create: `services/core/src/main/java/com/chessapp/chess/PgnTagValues.java`
- Test: `services/core/src/test/java/com/chessapp/chess/PgnTagValuesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `public static String PgnTagValues.optional(String)`, `public static LocalDate PgnTagValues.date(String)`, `public static Integer PgnTagValues.rating(String)`, `public static String PgnTagValues.eco(String)`. Task 7 uses all four.

- [ ] **Step 1: Write the failing test**

Create `PgnTagValuesTest`:

```java
package com.chessapp.chess;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PgnTagValuesTest {

    @Nested
    class Optional {

        @Test
        void trimsAValueThatIsPresent() {
            assertThat(PgnTagValues.optional("  Club Championship  ")).isEqualTo("Club Championship");
        }

        @Test
        void treatsAbsentBlankAndTheUnknownMarkerAsTheSameThing() {
            assertThat(PgnTagValues.optional(null)).isNull();
            assertThat(PgnTagValues.optional("   ")).isNull();
            assertThat(PgnTagValues.optional("?")).isNull();
        }
    }

    @Nested
    class Date {

        @Test
        void readsAFullyKnownDate() {
            assertThat(PgnTagValues.date("2026.03.14")).isEqualTo(LocalDate.of(2026, 3, 14));
        }

        @Test
        void returnsNullForAWhollyUnknownDate() {
            assertThat(PgnTagValues.date("????.??.??")).isNull();
        }

        @Test
        void returnsNullForAPartiallyKnownDateBecauseTheColumnStoresNoPrecision() {
            assertThat(PgnTagValues.date("2026.??.??")).isNull();
            assertThat(PgnTagValues.date("2026.03.??")).isNull();
        }

        @Test
        void returnsNullForADateThatCannotExist() {
            assertThat(PgnTagValues.date("2026.02.30")).isNull();
            assertThat(PgnTagValues.date("2026.13.01")).isNull();
        }

        @Test
        void returnsNullForAbsentOrMalformedInput() {
            assertThat(PgnTagValues.date(null)).isNull();
            assertThat(PgnTagValues.date("14 March 2026")).isNull();
        }
    }

    @Nested
    class Rating {

        @Test
        void readsANumericRating() {
            assertThat(PgnTagValues.rating("1850")).isEqualTo(1850);
        }

        @Test
        void returnsNullRatherThanRejectingAnUnusableRating() {
            assertThat(PgnTagValues.rating(null)).isNull();
            assertThat(PgnTagValues.rating("?")).isNull();
            assertThat(PgnTagValues.rating("   ")).isNull();
            assertThat(PgnTagValues.rating("unrated")).isNull();
            assertThat(PgnTagValues.rating("0")).isNull();
            assertThat(PgnTagValues.rating("-100")).isNull();
        }

        @Test
        void returnsNullForANumberTooLargeToBeARating() {
            assertThat(PgnTagValues.rating("99999999999999999999")).isNull();
        }
    }

    @Nested
    class Eco {

        @Test
        void readsAWellFormedCode() {
            assertThat(PgnTagValues.eco("C60")).isEqualTo("C60");
        }

        @Test
        void returnsNullRatherThanRejectingAMalformedCode() {
            assertThat(PgnTagValues.eco("F60")).isNull();
            assertThat(PgnTagValues.eco("C6")).isNull();
            assertThat(PgnTagValues.eco("?")).isNull();
            assertThat(PgnTagValues.eco(null)).isNull();
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o test -Dtest=PgnTagValuesTest`
Expected: FAIL — compilation error, `cannot find symbol: class PgnTagValues`.

- [ ] **Step 3: Write the normaliser**

Create `services/core/src/main/java/com/chessapp/chess/PgnTagValues.java`:

```java
package com.chessapp.chess;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/**
 * Turns raw PGN tag values into the types the rest of the application uses.
 *
 * <p>Values that cannot be used become null rather than rejecting the game.
 * A decorative tag is not worth failing an import over, and ADR 0002 already
 * accepts the resulting precision loss for dates. What a document says about the
 * moves is a different matter, and is validated rather than normalised.
 */
public final class PgnTagValues {

    /** The PGN marker for an unknown tag value. */
    private static final String UNKNOWN = "?";

    private static final Pattern ECO = Pattern.compile("[A-E][0-9]{2}");

    private static final Pattern RATING = Pattern.compile("[0-9]{1,6}");

    /** STRICT rejects 2026.02.30, which SMART would silently move to the 28th. */
    private static final DateTimeFormatter PGN_DATE =
            DateTimeFormatter.ofPattern("uuuu.MM.dd").withResolverStyle(ResolverStyle.STRICT);

    private PgnTagValues() {
    }

    /** Absent, blank and {@code ?} all mean the same thing, so all three become null. */
    public static String optional(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() || UNKNOWN.equals(trimmed) ? null : trimmed;
    }

    /**
     * Set only when the date is fully known and real. ADR 0002 stores no precision,
     * so a partial date is null and the original tag survives in {@code source_pgn}.
     */
    public static LocalDate date(String raw) {
        String value = optional(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, PGN_DATE);
        } catch (DateTimeParseException notAFullyKnownDate) {
            return null;
        }
    }

    public static Integer rating(String raw) {
        String value = optional(raw);
        if (value == null || !RATING.matcher(value).matches()) {
            return null;
        }
        int rating = Integer.parseInt(value);
        return rating > 0 ? rating : null;
    }

    public static String eco(String raw) {
        String value = optional(raw);
        return value != null && ECO.matcher(value).matches() ? value : null;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o test -Dtest=PgnTagValuesTest`
Expected: PASS, all green.

- [ ] **Step 5: Commit**

```bash
git add services/core/src/main/java/com/chessapp/chess/PgnTagValues.java \
        services/core/src/test/java/com/chessapp/chess/PgnTagValuesTest.java
git commit -m "$(cat <<'EOF'
Normalise PGN tag values into our own types

Interpreting 2026.??.??, ? and a non-numeric rating is knowledge about PGN
documents rather than about a Game, so it belongs with the reader and the
import endpoint receives values already in our types.

Unusable decorative values become null rather than failing the import. A
partial date matches ADR 0002's played_on column, which stores no precision,
and the original tag survives in source_pgn either way.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Add chesslib and validate moves by replay

The heart of the change. No chesslib path rejects an illegal pawn move, so every move is replayed against `Board.legalMoves()`.

**Files:**
- Modify: `services/core/pom.xml`
- Create: `services/core/src/main/java/com/chessapp/chess/chesslib/ValidatedMoves.java`
- Create: `services/core/src/main/java/com/chessapp/chess/chesslib/IllegalMoveAtPly.java`
- Test: `services/core/src/test/java/com/chessapp/chess/chesslib/ValidatedMovesTest.java`

**Interfaces:**
- Consumes: `com.chessapp.game.domain.GameResult`.
- Produces: package-private `record ValidatedMoves(String movetext, GameResult terminalResult)` with `static ValidatedMoves of(MoveList moves)`, and `IllegalMoveAtPly` with `int ply()`. Task 7 uses both. `terminalResult` is null when the final position is not checkmate or stalemate.

- [ ] **Step 1: Add the dependency**

In `services/core/pom.xml`, add a `<repositories>` block immediately after the closing `</properties>` tag:

```xml
    <!--
      chesslib is published through JitPack rather than Maven Central. ADR 0001
      accepted that, on the conditions that the version is pinned exactly and CI
      caches the Maven repository so builds do not hit JitPack every run.
    -->
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>
```

And add this dependency inside `<dependencies>`, after the `spring-boot-starter-actuator` entry:

```xml
        <!--
          Chess rules: legal move generation, SAN parsing and PGN reading.
          Pinned exactly, never a branch reference or -SNAPSHOT.
          Confined to com.chessapp.chess.chesslib by ADR 0001.
        -->
        <dependency>
            <groupId>com.github.bhlangonijr</groupId>
            <artifactId>chesslib</artifactId>
            <version>1.3.7</version>
        </dependency>
```

- [ ] **Step 2: Verify the dependency resolves**

Run (online, once — drop `-o`): `mvn dependency:resolve -Dsilent=true`
Expected: BUILD SUCCESS, no missing artifact for `com.github.bhlangonijr:chesslib:1.3.7`.

If JitPack is slow on first fetch it may take a minute while it builds the artifact from the tag; that is expected and only happens once.

- [ ] **Step 3: Write the failing test**

Create `ValidatedMovesTest`:

```java
package com.chessapp.chess.chesslib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chessapp.game.domain.GameResult;
import com.github.bhlangonijr.chesslib.move.MoveList;
import org.junit.jupiter.api.Test;

class ValidatedMovesTest {

    private static MoveList moves(String san) {
        MoveList moves = new MoveList();
        moves.loadFromSan(san);
        return moves;
    }

    @Test
    void returnsNormalisedMovetextWithNoTrailingSpace() {
        ValidatedMoves validated = ValidatedMoves.of(moves("1. e4 e5 2. Nf3 Nc6"));

        assertThat(validated.movetext()).isEqualTo("1. e4 e5 2. Nf3 Nc6");
    }

    @Test
    void rejectsAPawnMovingThreeSquaresWhichChesslibItselfAccepts() {
        MoveList accepted = moves("1. e5 e5");

        assertThat(accepted.toSanWithMoveNumbers().trim())
                .as("chesslib accepts this, which is why the replay exists")
                .isEqualTo("1. e5 exe5");
        assertThatThrownBy(() -> ValidatedMoves.of(accepted))
                .isInstanceOf(IllegalMoveAtPly.class)
                .hasMessageContaining("not legal");
    }

    @Test
    void reportsThePlyAndMoveNumberOfTheFirstIllegalMove() {
        assertThatThrownBy(() -> ValidatedMoves.of(moves("1. e4 e5 2. Nf3 Nc6 3. e6")))
                .isInstanceOfSatisfying(IllegalMoveAtPly.class,
                        illegal -> assertThat(illegal.ply()).isEqualTo(5))
                .hasMessageContaining("3.");
    }

    @Test
    void reportsCheckmateAsAWinForTheSideThatDeliveredIt() {
        ValidatedMoves validated = ValidatedMoves.of(moves("1. f3 e5 2. g4 Qh4#"));

        assertThat(validated.terminalResult()).isEqualTo(GameResult.BLACK_WON);
    }

    @Test
    void reportsNoTerminalResultForAnOrdinaryPosition() {
        assertThat(ValidatedMoves.of(moves("1. e4 e5")).terminalResult()).isNull();
    }

    @Test
    void keepsTheCheckAndMateSuffixesChesslibEmits() {
        assertThat(ValidatedMoves.of(moves("1. f3 e5 2. g4 Qh4#")).movetext())
                .endsWith("Qh4#");
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn -o test -Dtest=ValidatedMovesTest`
Expected: FAIL — compilation error, `cannot find symbol: class ValidatedMoves`.

- [ ] **Step 5: Write the failure type**

Create `services/core/src/main/java/com/chessapp/chess/chesslib/IllegalMoveAtPly.java`:

```java
package com.chessapp.chess.chesslib;

/**
 * Thrown by {@link ValidatedMoves} when a move is not legal in the position it is
 * played from. Carries the ply so the parser can report where the game stopped
 * making sense.
 */
class IllegalMoveAtPly extends RuntimeException {

    private final int ply;

    IllegalMoveAtPly(int ply, String move) {
        super("move " + moveNumber(ply) + (isWhiteMove(ply) ? ". " : "... ") + move
                + " is not legal in this position");
        this.ply = ply;
    }

    /** 1-based half-move index. Ply 1 is White's first move. */
    int ply() {
        return ply;
    }

    private static int moveNumber(int ply) {
        return (ply + 1) / 2;
    }

    private static boolean isWhiteMove(int ply) {
        return ply % 2 == 1;
    }
}
```

- [ ] **Step 6: Write the validator**

Create `services/core/src/main/java/com/chessapp/chess/chesslib/ValidatedMoves.java`:

```java
package com.chessapp.chess.chesslib;

import com.chessapp.game.domain.GameResult;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;

/**
 * Moves that have been checked for legality, with whatever the final position
 * says about the result.
 *
 * <p>Every move is replayed against {@link Board#legalMoves()}. This is not
 * belt-and-braces: no chesslib path rejects an illegal pawn move.
 * {@code MoveList.loadFromSan("1. e5 e5")} is accepted and yields
 * {@code 1. e5 exe5}, a white pawn moving three squares from e2, and the PGN
 * reader accepts the same input. ADR 0001 originally named SAN parsed through
 * {@code MoveList} as an authoritative path; it was corrected when this was found.
 *
 * <p>Deleting the replay loop as redundant would silently store illegal games.
 * {@code ChesslibContractTest} exists to fail if anyone does.
 *
 * @param movetext       normalised SAN with move numbers, trimmed
 * @param terminalResult the result the final position forces, or null when the
 *                       position is neither checkmate nor stalemate
 */
record ValidatedMoves(String movetext, GameResult terminalResult) {

    static ValidatedMoves of(MoveList moves) {
        Board board = new Board();
        int ply = 0;
        for (Move move : moves) {
            ply++;
            if (!board.legalMoves().contains(move)) {
                throw new IllegalMoveAtPly(ply, sanAt(moves, ply, move));
            }
            board.doMove(move);
        }
        return new ValidatedMoves(moves.toSanWithMoveNumbers().trim(), terminalResult(board));
    }

    /**
     * The side to move is the side that has been mated, so the other side won.
     */
    private static GameResult terminalResult(Board board) {
        if (board.isMated()) {
            return board.getSideToMove() == Side.WHITE ? GameResult.BLACK_WON : GameResult.WHITE_WON;
        }
        return board.isStaleMate() ? GameResult.DRAW : null;
    }

    /**
     * SAN for the error message, falling back to the move's own coordinate form.
     * Rendering SAN for a list containing an illegal move does work, but it is
     * formatting an input the library already mis-decoded, so it is not trusted.
     */
    private static String sanAt(MoveList moves, int ply, Move move) {
        try {
            String[] san = moves.toSanArray();
            return ply <= san.length ? san[ply - 1] : move.toString();
        } catch (RuntimeException notRenderable) {
            return move.toString();
        }
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -o test -Dtest=ValidatedMovesTest`
Expected: PASS, all green.

- [ ] **Step 8: Verify the import boundary holds**

Run: `grep -rl "com.github.bhlangonijr" services/core/src/main/java | grep -v "/chess/chesslib/"`
Expected: no output. Any file listed violates ADR 0001 and must be fixed before committing.

- [ ] **Step 9: Commit**

```bash
git add services/core/pom.xml \
        services/core/src/main/java/com/chessapp/chess/chesslib/ \
        services/core/src/test/java/com/chessapp/chess/chesslib/
git commit -m "$(cat <<'EOF'
Validate moves by replaying against legalMoves()

No chesslib path rejects an illegal pawn move. loadFromSan("1. e5 e5") is
accepted and yields "1. e5 exe5" — a white pawn moving three squares from e2 —
and the PGN reader accepts the same input. Illegal piece moves and castling are
rejected; the hole is in pawn decoding.

So every move is replayed against Board.legalMoves(), which is also what gives
an illegal move its ply. ADR 0001 has been corrected; the class comment records
why the loop is not redundant, because deleting it would silently store illegal
games.

Adds chesslib 1.3.7 from JitPack, pinned exactly, confined to one package.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: The parser contract and its implementation

**Files:**
- Create: `services/core/src/main/java/com/chessapp/chess/PgnParser.java`
- Create: `services/core/src/main/java/com/chessapp/chess/PgnParseResult.java`
- Create: `services/core/src/main/java/com/chessapp/chess/ParsedGame.java`
- Create: `services/core/src/main/java/com/chessapp/chess/PgnError.java`
- Create: `services/core/src/main/java/com/chessapp/chess/PgnErrorCode.java`
- Modify: `services/core/src/main/java/com/chessapp/game/domain/GameResult.java`
- Create: `services/core/src/main/java/com/chessapp/chess/chesslib/ChesslibPgnParser.java`
- Test: `services/core/src/test/java/com/chessapp/chess/chesslib/ChesslibPgnParserTest.java`

**Interfaces:**
- Consumes: `PgnTagReader.tags`/`movetext`, all four `PgnTagValues` methods, `ValidatedMoves.of`, `IllegalMoveAtPly.ply`.
- Produces: `PgnParseResult PgnParser.parse(String pgn)`; records `PgnParseResult.Parsed(ParsedGame game)` and `PgnParseResult.Rejected(PgnError error)`; `ParsedGame(String event, String site, LocalDate playedOn, String round, String whiteName, String blackName, Integer whiteRating, Integer blackRating, String eco, GameResult result, String movetext)`; `PgnError(PgnErrorCode code, String message, Integer ply)`; `GameResult.fromPgnToken(String)` returning null for anything unrecognised. #7 consumes all of these.

- [ ] **Step 1: Write the value types**

Create `PgnErrorCode.java`:

```java
package com.chessapp.chess;

/** Why a PGN document was rejected. */
public enum PgnErrorCode {

    NOT_PGN,
    MULTIPLE_GAMES,
    NON_STANDARD_START_POSITION,
    NO_MOVES,
    UNREADABLE_MOVE,
    ILLEGAL_MOVE,
    PLAYER_UNKNOWN,
    RESULT_MISSING,
    RESULT_CONFLICT,
    RESULT_CONTRADICTS_POSITION
}
```

Create `PgnError.java`:

```java
package com.chessapp.chess;

/**
 * @param ply 1-based half-move index where the problem is, or null when the
 *            problem is not about a specific move. Ply 1 is White's first move.
 */
public record PgnError(PgnErrorCode code, String message, Integer ply) {

    public PgnError(PgnErrorCode code, String message) {
        this(code, message, null);
    }
}
```

Create `ParsedGame.java`:

```java
package com.chessapp.chess;

import com.chessapp.game.domain.GameResult;
import java.time.LocalDate;

/**
 * The chess facts a PGN document yields, already in our own types.
 *
 * <p>Tags we do not model — {@code TimeControl}, {@code Termination},
 * {@code Annotator}, provider-specific ones — are not here. ADR 0002 keeps them
 * recoverable from {@code source_pgn}, which the import endpoint stores.
 *
 * <p>{@code movetext} satisfies the rules {@code Game.movetext} enforces: SAN with
 * move numbers, no tag pairs, no terminal result token.
 */
public record ParsedGame(String event,
                         String site,
                         LocalDate playedOn,
                         String round,
                         String whiteName,
                         String blackName,
                         Integer whiteRating,
                         Integer blackRating,
                         String eco,
                         GameResult result,
                         String movetext) {
}
```

Create `PgnParseResult.java`:

```java
package com.chessapp.chess;

/**
 * Sealed, so a caller cannot forget that parsing can fail. An invalid document is
 * an expected outcome for an import fed by users, not an exceptional condition.
 */
public sealed interface PgnParseResult {

    record Parsed(ParsedGame game) implements PgnParseResult {
    }

    record Rejected(PgnError error) implements PgnParseResult {
    }
}
```

Create `PgnParser.java`:

```java
package com.chessapp.chess;

/**
 * Reads a PGN document into validated chess facts.
 *
 * <p>An interface with one implementation, because ADR 0001 names
 * wolfraam/chess-game as the fallback if chesslib is abandoned, and a seam is what
 * makes that a real option rather than a sentence in a document.
 */
public interface PgnParser {

    /** Never throws for bad input: an unusable document comes back as a rejection. */
    PgnParseResult parse(String pgn);
}
```

- [ ] **Step 2: Add `fromPgnToken` to `GameResult`**

In `services/core/src/main/java/com/chessapp/game/domain/GameResult.java`, add:

```java
    /**
     * The result a PGN token denotes, or null when the token is not one of the four
     * the specification defines. A document carrying anything else is treated as
     * having said nothing, rather than as having said something wrong.
     */
    public static GameResult fromPgnToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        for (GameResult result : values()) {
            if (result.pgnToken().equals(trimmed)) {
                return result;
            }
        }
        return null;
    }
```

- [ ] **Step 3: Write the failing test**

Create `ChesslibPgnParserTest`:

```java
package com.chessapp.chess.chesslib;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.chess.ParsedGame;
import com.chessapp.chess.PgnErrorCode;
import com.chessapp.chess.PgnParseResult;
import com.chessapp.game.domain.GameResult;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChesslibPgnParserTest {

    private final ChesslibPgnParser parser = new ChesslibPgnParser();

    private ParsedGame parsed(String pgn) {
        PgnParseResult result = parser.parse(pgn);
        assertThat(result).isInstanceOf(PgnParseResult.Parsed.class);
        return ((PgnParseResult.Parsed) result).game();
    }

    private PgnErrorCode rejectedCode(String pgn) {
        PgnParseResult result = parser.parse(pgn);
        assertThat(result).isInstanceOf(PgnParseResult.Rejected.class);
        return ((PgnParseResult.Rejected) result).error().code();
    }

    private static final String COMPLETE = """
            [Event "Club Championship"]
            [Site "London ENG"]
            [Date "2026.03.14"]
            [Round "3.2"]
            [White "Green, Guy"]
            [Black "Club Opponent"]
            [Result "1-0"]
            [WhiteElo "1850"]
            [BlackElo "?"]
            [ECO "C60"]
            [TimeControl "40/7200"]

            1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
            """;

    @Nested
    class SuccessfulParse {

        @Test
        void readsEveryModelledTag() {
            ParsedGame game = parsed(COMPLETE);

            assertThat(game.event()).isEqualTo("Club Championship");
            assertThat(game.site()).isEqualTo("London ENG");
            assertThat(game.playedOn()).isEqualTo(LocalDate.of(2026, 3, 14));
            assertThat(game.whiteName()).isEqualTo("Green, Guy");
            assertThat(game.blackName()).isEqualTo("Club Opponent");
            assertThat(game.whiteRating()).isEqualTo(1850);
            assertThat(game.blackRating()).isNull();
            assertThat(game.eco()).isEqualTo("C60");
            assertThat(game.result()).isEqualTo(GameResult.WHITE_WON);
        }

        @Test
        void keepsTheRoundTagVerbatim() {
            assertThat(parsed(COMPLETE).round()).isEqualTo("3.2");
        }

        @Test
        void producesMovetextWithNoTagPairsAndNoResultToken() {
            assertThat(parsed(COMPLETE).movetext()).isEqualTo("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
        }

        @Test
        void dropsCommentsNagsAndVariations() {
            String annotated = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    1. e4 {good} e5 $1 (1... c5 2. Nf3) 2. Nf3 *
                    """;

            assertThat(parsed(annotated).movetext()).isEqualTo("1. e4 e5 2. Nf3");
        }

        @Test
        void takesTheResultFromTheTerminalTokenWhenThereIsNoResultTag() {
            String noTag = """
                    [White "A"]
                    [Black "B"]

                    1. e4 e5 0-1
                    """;

            assertThat(parsed(noTag).result()).isEqualTo(GameResult.BLACK_WON);
        }

        @Test
        void acceptsADecisiveResultInANonTerminalPositionBecauseResignationIsNotOnTheBoard() {
            String resigned = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 1-0
                    """;

            assertThat(parsed(resigned).result()).isEqualTo(GameResult.WHITE_WON);
        }
    }

    @Nested
    class Rejections {

        @Test
        void rejectsNullAndBlankInput() {
            assertThat(rejectedCode(null)).isEqualTo(PgnErrorCode.NOT_PGN);
            assertThat(rejectedCode("   ")).isEqualTo(PgnErrorCode.NOT_PGN);
        }

        @Test
        void rejectsTextThatIsNotPgnAtAll() {
            assertThat(rejectedCode("this is not a chess game")).isEqualTo(PgnErrorCode.NOT_PGN);
        }

        @Test
        void rejectsADocumentHoldingMoreThanOneGame() {
            String two = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 1-0

                    [White "C"]
                    [Black "D"]
                    [Result "0-1"]

                    1. d4 d5 0-1
                    """;

            assertThat(rejectedCode(two)).isEqualTo(PgnErrorCode.MULTIPLE_GAMES);
        }

        @Test
        void rejectsAGameStartingFromANonStandardPosition() {
            String study = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]
                    [SetUp "1"]
                    [FEN "8/8/8/8/8/5k2/6q1/7K b - - 0 1"]

                    1... Qg1+ *
                    """;

            assertThat(rejectedCode(study)).isEqualTo(PgnErrorCode.NON_STANDARD_START_POSITION);
        }

        @Test
        void rejectsAGameWithNoMoves() {
            String empty = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    *
                    """;

            assertThat(rejectedCode(empty)).isEqualTo(PgnErrorCode.NO_MOVES);
        }

        @Test
        void rejectsAnIllegalMoveAndSaysWhereItIs() {
            String illegal = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    1. e4 e5 2. Nf3 Nc6 3. e6 *
                    """;

            PgnParseResult result = parser.parse(illegal);

            assertThat(result).isInstanceOfSatisfying(PgnParseResult.Rejected.class, rejected -> {
                assertThat(rejected.error().code()).isEqualTo(PgnErrorCode.ILLEGAL_MOVE);
                assertThat(rejected.error().ply()).isEqualTo(5);
            });
        }

        @Test
        void rejectsSanThatCannotBeUnderstood() {
            String nonsense = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    1. e4 e5 2. Zz9 *
                    """;

            assertThat(rejectedCode(nonsense)).isEqualTo(PgnErrorCode.UNREADABLE_MOVE);
        }

        @Test
        void rejectsAGameWhosePlayerIsUnknown() {
            String unknown = """
                    [White "?"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 1-0
                    """;

            assertThat(rejectedCode(unknown)).isEqualTo(PgnErrorCode.PLAYER_UNKNOWN);
        }

        @Test
        void rejectsAGameWithNoResultAtAll() {
            String none = """
                    [White "A"]
                    [Black "B"]

                    1. e4 e5
                    """;

            assertThat(rejectedCode(none)).isEqualTo(PgnErrorCode.RESULT_MISSING);
        }

        @Test
        void rejectsAResultTagThatDisagreesWithTheTerminalToken() {
            String conflict = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]

                    1. e4 e5 0-1
                    """;

            assertThat(rejectedCode(conflict)).isEqualTo(PgnErrorCode.RESULT_CONFLICT);
        }

        @Test
        void rejectsADeclaredResultThatContradictsCheckmateOnTheBoard() {
            String wrong = """
                    [White "A"]
                    [Black "B"]
                    [Result "1-0"]

                    1. f3 e5 2. g4 Qh4# 1-0
                    """;

            assertThat(rejectedCode(wrong)).isEqualTo(PgnErrorCode.RESULT_CONTRADICTS_POSITION);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn -o test -Dtest=ChesslibPgnParserTest`
Expected: FAIL — compilation error, `cannot find symbol: class ChesslibPgnParser`.

- [ ] **Step 5: Write the parser**

Create `services/core/src/main/java/com/chessapp/chess/chesslib/ChesslibPgnParser.java`:

```java
package com.chessapp.chess.chesslib;

import com.chessapp.chess.ParsedGame;
import com.chessapp.chess.PgnError;
import com.chessapp.chess.PgnErrorCode;
import com.chessapp.chess.PgnParseResult;
import com.chessapp.chess.PgnParser;
import com.chessapp.chess.PgnTagReader;
import com.chessapp.chess.PgnTagValues;
import com.chessapp.game.domain.GameResult;
import com.github.bhlangonijr.chesslib.game.Game;
import com.github.bhlangonijr.chesslib.pgn.PgnIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The only {@link PgnParser} implementation, and with {@link ValidatedMoves} the
 * only place chesslib is used.
 *
 * <p>chesslib decodes SAN and strips annotations; it decides nothing. Tags are read
 * from the document by {@link PgnTagReader} because the library's parsed model
 * loses {@code Round} and cannot distinguish a missing {@code Result} from
 * {@code *}. Legality is decided by {@link ValidatedMoves}.
 *
 * <p>Checks run in a fixed order — structure, moves, players, result — so the same
 * document always yields the same error. Moves come before the player and result
 * checks because a game that does not reconstruct is broken in a way the user has
 * to fix first, and because the result check needs the final position.
 */
@Component
public class ChesslibPgnParser implements PgnParser {

    /** The PGN marker for an unknown tag value. */
    private static final String UNKNOWN = "?";

    @Override
    public PgnParseResult parse(String pgn) {
        if (pgn == null || pgn.isBlank()) {
            return rejected(PgnErrorCode.NOT_PGN, "no PGN text was supplied");
        }

        List<Game> games;
        try {
            games = readGames(pgn);
        } catch (RuntimeException unreadable) {
            return rejected(PgnErrorCode.NOT_PGN,
                    "the text could not be read as PGN: " + unreadable.getMessage());
        }
        if (games.isEmpty()) {
            return rejected(PgnErrorCode.NOT_PGN, "no game was found in the text");
        }
        if (games.size() > 1) {
            return rejected(PgnErrorCode.MULTIPLE_GAMES,
                    "the file holds " + games.size() + " games; import one game at a time");
        }

        Game game = games.get(0);
        Map<String, String> tags = PgnTagReader.tags(pgn);
        if (game.getFen() != null || tags.containsKey("FEN") || tags.containsKey("SetUp")) {
            return rejected(PgnErrorCode.NON_STANDARD_START_POSITION,
                    "games that start from a position other than the standard one are not"
                            + " supported yet");
        }

        String movetext = PgnTagReader.movetext(pgn);
        String terminalToken = terminalToken(movetext);
        if (withoutTerminalToken(movetext, terminalToken).isBlank()) {
            return rejected(PgnErrorCode.NO_MOVES, "the game has no moves");
        }

        ValidatedMoves moves;
        try {
            game.loadMoveText();
            moves = ValidatedMoves.of(game.getHalfMoves());
        } catch (IllegalMoveAtPly illegal) {
            return rejected(PgnErrorCode.ILLEGAL_MOVE, illegal.getMessage(), illegal.ply());
        } catch (RuntimeException unreadable) {
            return rejected(PgnErrorCode.UNREADABLE_MOVE,
                    "a move could not be understood: " + unreadable.getMessage());
        }

        PgnParseResult playerProblem = checkPlayers(tags);
        if (playerProblem != null) {
            return playerProblem;
        }

        GameResult fromTag = GameResult.fromPgnToken(tags.get("Result"));
        GameResult fromToken = GameResult.fromPgnToken(terminalToken);
        if (fromTag != null && fromToken != null && fromTag != fromToken) {
            return rejected(PgnErrorCode.RESULT_CONFLICT,
                    "the Result tag says " + fromTag.pgnToken() + " but the moves end with "
                            + fromToken.pgnToken());
        }
        GameResult declared = fromTag != null ? fromTag : fromToken;
        if (declared == null) {
            return rejected(PgnErrorCode.RESULT_MISSING,
                    "the game declares no result, as a Result tag or as a token after the moves");
        }
        if (moves.terminalResult() != null && moves.terminalResult() != declared) {
            return rejected(PgnErrorCode.RESULT_CONTRADICTS_POSITION,
                    "the game declares " + declared.pgnToken() + " but the final position is "
                            + moves.terminalResult().pgnToken());
        }

        return new PgnParseResult.Parsed(new ParsedGame(
                PgnTagValues.optional(tags.get("Event")),
                PgnTagValues.optional(tags.get("Site")),
                PgnTagValues.date(tags.get("Date")),
                PgnTagValues.optional(tags.get("Round")),
                tags.get("White").trim(),
                tags.get("Black").trim(),
                PgnTagValues.rating(tags.get("WhiteElo")),
                PgnTagValues.rating(tags.get("BlackElo")),
                PgnTagValues.eco(tags.get("ECO")),
                declared,
                moves.movetext()));
    }

    /**
     * The iterator validates lazily and throws from iteration itself, so the whole
     * loop is inside the caller's error handling rather than only the move loading.
     */
    private static List<Game> readGames(String pgn) {
        List<Game> games = new ArrayList<>();
        try (PgnIterator iterator = new PgnIterator(List.of(pgn.split("\\R", -1)))) {
            for (Game game : iterator) {
                games.add(game);
            }
        } catch (RuntimeException rethrow) {
            throw rethrow;
        } catch (Exception closeFailed) {
            throw new IllegalStateException(closeFailed);
        }
        return games;
    }

    private static PgnParseResult checkPlayers(Map<String, String> tags) {
        for (String colour : new String[] {"White", "Black"}) {
            String name = tags.get(colour);
            if (name == null || name.isBlank() || UNKNOWN.equals(name.trim())) {
                return rejected(PgnErrorCode.PLAYER_UNKNOWN,
                        "the " + colour + " player is not named; a game with an unknown player"
                                + " cannot be stored");
            }
        }
        return null;
    }

    private static String terminalToken(String movetext) {
        if (movetext.isBlank()) {
            return null;
        }
        String[] tokens = movetext.trim().split("\\s+");
        String last = tokens[tokens.length - 1];
        return GameResult.fromPgnToken(last) != null ? last : null;
    }

    private static String withoutTerminalToken(String movetext, String terminalToken) {
        if (terminalToken == null) {
            return movetext;
        }
        return movetext.substring(0, movetext.lastIndexOf(terminalToken));
    }

    private static PgnParseResult rejected(PgnErrorCode code, String message) {
        return new PgnParseResult.Rejected(new PgnError(code, message));
    }

    private static PgnParseResult rejected(PgnErrorCode code, String message, Integer ply) {
        return new PgnParseResult.Rejected(new PgnError(code, message, ply));
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -o test -Dtest=ChesslibPgnParserTest`
Expected: PASS, all green.

If `rejectsSanThatCannotBeUnderstood` instead reports `NOT_PGN`, the library threw during iteration rather than during move loading for that input. That is acceptable behaviour but the test must then assert what actually happens — change the assertion to accept `NOT_PGN` and note it in the test name, rather than contorting the parser.

- [ ] **Step 7: Verify the import boundary still holds**

Run: `grep -rl "com.github.bhlangonijr" services/core/src/main/java | grep -v "/chess/chesslib/"`
Expected: no output.

- [ ] **Step 8: Run the whole suite**

Run: `mvn -o clean verify`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add services/core/src/main/java/com/chessapp/chess/ \
        services/core/src/main/java/com/chessapp/game/domain/GameResult.java \
        services/core/src/test/java/com/chessapp/chess/
git commit -m "$(cat <<'EOF'
Parse PGN documents into validated chess facts

An invalid document comes back as a Rejected rather than an exception: for an
import fed by users that is an expected outcome, and a sealed result makes it
impossible to forget at the call site.

Checks run in a fixed order — structure, moves, players, result — so the same
document always produces the same error. Moves come before the player and
result checks because a game that does not reconstruct is broken in a way the
user must fix first, and because the result check needs the final position.

chesslib decodes SAN and strips annotations; it decides nothing. Tags come from
our own reader and legality from our own replay.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Library contract tests and the round-trip property

These tests exist to fail. Each pins a behaviour we depend on, so a chesslib upgrade that regresses one breaks the build instead of corrupting a game.

**Files:**
- Test: `services/core/src/test/java/com/chessapp/chess/chesslib/ChesslibContractTest.java`
- Test: `services/core/src/test/java/com/chessapp/chess/PgnRoundTripTest.java`

**Interfaces:**
- Consumes: everything from Tasks 3–7.
- Produces: nothing.

- [ ] **Step 1: Write the contract test**

Create `ChesslibContractTest`:

```java
package com.chessapp.chess.chesslib;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.chess.PgnErrorCode;
import com.chessapp.chess.PgnParseResult;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * One test per constraint recorded in ADR 0001, expressed through our own API.
 *
 * <p>These are assertions about behaviour we depend on, not about behaviour we
 * implement. A library upgrade that breaks one should fail this build rather than
 * quietly change what gets stored.
 */
class ChesslibContractTest {

    private final ChesslibPgnParser parser = new ChesslibPgnParser();

    private static String game(String movetext) {
        return """
                [White "A"]
                [Black "B"]
                [Result "*"]

                """ + movetext + "\n";
    }

    private PgnParseResult.Rejected rejected(String pgn) {
        PgnParseResult result = parser.parse(pgn);
        assertThat(result).isInstanceOf(PgnParseResult.Rejected.class);
        return (PgnParseResult.Rejected) result;
    }

    /**
     * Constraint 1, and the reason the replay loop exists. chesslib accepts this
     * input through every path it offers, so this test passes only while our own
     * legality check is in place and fails the moment someone removes it.
     */
    @Test
    void rejectsAPawnMovingThreeSquaresWhichChesslibAcceptsThroughEveryPathItOffers() {
        PgnParseResult.Rejected rejected = rejected(game("1. e5 e5 *"));

        assertThat(rejected.error().code()).isEqualTo(PgnErrorCode.ILLEGAL_MOVE);
        assertThat(rejected.error().ply()).isEqualTo(1);
    }

    /** Constraint 2: whatever unchecked type the library throws, and wherever. */
    @Test
    void turnsSanCapturingTheKingIntoARejectionRatherThanAnUncheckedException() {
        PgnParseResult.Rejected rejected = rejected(game("1. e4 e5 2. Qh5 Nc6 3. Qxe8 *"));

        assertThat(rejected.error().code())
                .isIn(PgnErrorCode.NOT_PGN, PgnErrorCode.UNREADABLE_MOVE, PgnErrorCode.ILLEGAL_MOVE);
        assertThat(rejected.error().message()).isNotBlank();
    }

    /** Constraint 3: the iterator returns a game before its moves have been read. */
    @Test
    void rejectsADocumentWhoseMovesAreInvalidRatherThanReturningTheUnverifiedGame() {
        assertThat(rejected(game("1. e4 e5 2. Nf3 Nc6 3. e6 *")).error().code())
                .isEqualTo(PgnErrorCode.ILLEGAL_MOVE);
    }

    /** Constraint 3, second half: no moves at all throws inside the library. */
    @Test
    void rejectsAGameWithNoMovesRatherThanThrowingFromInsideTheLibrary() {
        assertThat(rejected(game("*")).error().code()).isEqualTo(PgnErrorCode.NO_MOVES);
    }

    /** Constraint 4: the terminal token must not reach movetext. */
    @Test
    void keepsTheTerminalResultTokenOutOfMovetext() {
        PgnParseResult result = parser.parse("""
                [White "A"]
                [Black "B"]
                [Result "1-0"]

                1. e4 e5 2. Nf3 Nc6 1-0
                """);

        assertThat(result).isInstanceOfSatisfying(PgnParseResult.Parsed.class, parsed ->
                assertThat(parsed.game().movetext()).isEqualTo("1. e4 e5 2. Nf3 Nc6"));
    }

    /** Constraint 6: a Board per operation, never shared. */
    @Test
    void parsesConcurrentlyWithoutInterference() throws Exception {
        String pgn = """
                [White "A"]
                [Black "B"]
                [Result "1-0"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """;
        Callable<String> parse = () -> {
            PgnParseResult result = parser.parse(pgn);
            return ((PgnParseResult.Parsed) result).game().movetext();
        };

        try (ExecutorService threads = Executors.newFixedThreadPool(8)) {
            List<Future<String>> results = threads.invokeAll(java.util.Collections.nCopies(64, parse));
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
            }
        }
    }
}
```

Constraint 5 is asserted by `CanonicalPgnTest.injectsNoneOfTheTagsChesslibWouldAdd`, written in Task 3.

- [ ] **Step 2: Run the contract test**

Run: `mvn -o test -Dtest=ChesslibContractTest`
Expected: PASS, all green. If `rejectsAPawnMovingThreeSquares...` fails, the replay loop is missing or broken — that is the whole point of the test.

- [ ] **Step 3: Write the round-trip test**

Create `services/core/src/test/java/com/chessapp/chess/PgnRoundTripTest.java`:

```java
package com.chessapp.chess;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.game.domain.CanonicalPgn;
import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import com.chessapp.chess.chesslib.ChesslibPgnParser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Parse, build a Game, assemble, parse again. This is what makes "a game has
 * exactly one canonical PGN" true in practice rather than by assertion: assembly
 * is deterministic, and what it emits is what the parser reads back.
 */
class PgnRoundTripTest {

    private static final UUID ID = UUID.fromString("019535d9-5b22-7f04-8e15-3c9a7d2f6b81");
    private static final UUID WHITE_ID = UUID.fromString("019535d9-3df7-79fb-b466-fa907fa17f9e");
    private static final UUID BLACK_ID = UUID.fromString("019535d9-4aa1-7c2e-9d31-2b6f1c4e8a70");

    private final PgnParser parser = new ChesslibPgnParser();

    private ParsedGame parse(String pgn) {
        PgnParseResult result = parser.parse(pgn);
        assertThat(result).as("parsing %s", pgn).isInstanceOf(PgnParseResult.Parsed.class);
        return ((PgnParseResult.Parsed) result).game();
    }

    private static Game gameFrom(ParsedGame parsed) {
        return new Game(ID,
                new GameSide(WHITE_ID, parsed.whiteName(), parsed.whiteRating()),
                new GameSide(BLACK_ID, parsed.blackName(), parsed.blackRating()),
                parsed.event(), parsed.site(), parsed.round(), parsed.playedOn(),
                parsed.result(), parsed.eco(), GameSource.PGN_IMPORT, parsed.movetext(), null);
    }

    @Test
    void assemblingWhatWasParsedProducesADocumentThatParsesBackTheSame() {
        String original = """
                [Event "Club Championship"]
                [Site "London ENG"]
                [Date "2026.03.14"]
                [Round "3.2"]
                [White "Green, Guy"]
                [Black "Club Opponent"]
                [Result "1-0"]
                [WhiteElo "1850"]
                [BlackElo "1720"]
                [ECO "C60"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """;

        ParsedGame first = parse(original);
        String assembled = CanonicalPgn.from(gameFrom(first));
        ParsedGame second = parse(assembled);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void assemblyIsStableSoASecondPassChangesNothing() {
        String original = """
                [White "Green, Guy"]
                [Black "Club Opponent"]
                [Result "1/2-1/2"]

                1. d4 d5 2. c4 e6 1/2-1/2
                """;

        String once = CanonicalPgn.from(gameFrom(parse(original)));
        String twice = CanonicalPgn.from(gameFrom(parse(once)));

        assertThat(twice).isEqualTo(once);
    }

    @Test
    void survivesAGameThatEndsInCheckmate() {
        String mate = """
                [White "A"]
                [Black "B"]
                [Result "0-1"]

                1. f3 e5 2. g4 Qh4# 0-1
                """;

        ParsedGame first = parse(mate);

        assertThat(parse(CanonicalPgn.from(gameFrom(first)))).isEqualTo(first);
    }
}
```

This test lives in `com.chessapp.chess` rather than the `chesslib` sub-package, because the property it checks is about the contract, not about the implementation: any future `PgnParser` must satisfy it too. That is why Task 7 declares `ChesslibPgnParser` public.

- [ ] **Step 4: Run the round-trip test**

Run: `mvn -o test -Dtest=PgnRoundTripTest`
Expected: PASS, all green.

- [ ] **Step 5: Run the whole suite**

Run: `mvn -o clean verify`
Expected: BUILD SUCCESS, with every unit and integration test green.

- [ ] **Step 6: Commit**

```bash
git add services/core/src/test/java/com/chessapp/chess/
git commit -m "$(cat <<'EOF'
Pin the chesslib behaviour we depend on

One test per constraint in ADR 0001, expressed through our own API rather than
the library's, so an upgrade that regresses one fails the build instead of
quietly changing what gets stored.

The pawn-move test is the one that matters: chesslib accepts a three-square
pawn push through every path it offers, so the test passes only while our own
replay loop exists.

Adds the round-trip property — parse, build a Game, assemble, parse again —
which is what makes "a game has exactly one canonical PGN" true in practice
rather than by assertion.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review

**Spec coverage.** Every spec section maps to a task: build changes and the replay loop to Task 6; the tag reader to Task 4 and value normalisation to Task 5; the parser contract, validation table, result resolution and check ordering to Task 7; canonical assembly with escaping and line endings to Task 3; the control-character rule and `V3` to Task 2; the ADR corrections to Task 1; contract and round-trip tests to Task 8. Out-of-scope items (ECO classification, FEN and Zobrist exposure, multi-game uploads, the endpoint) appear in no task, which is correct.

**Known gaps, stated rather than hidden.**

- `UNREADABLE_MOVE` carries no ply. chesslib reports a FEN, not a position index, and bisecting to find the ply would be more machinery than the message is worth. The spec was amended to say ply is best-effort for this code.
- A malformed `ECO` such as `F60` becomes null rather than an error. The validation table has no ECO code, and rejecting an otherwise valid game over a decorative tag would be worse. Same treatment as a non-numeric rating.
- An unrecognised `Result` tag value falls back to the terminal token, and to `RESULT_MISSING` if there is none. A document that says something we do not understand is treated as having said nothing.
- Task 7 Step 6 and Task 8 Step 3 each name a specific fallback if reality differs from what this plan predicts. Take the fallback and note it; do not contort the code to satisfy a prediction.

**Type consistency.** `PgnTagReader.tags`/`movetext`, `PgnTagValues.optional`/`date`/`rating`/`eco`, `ValidatedMoves.of`/`movetext`/`terminalResult`, `IllegalMoveAtPly.ply`, `GameResult.fromPgnToken`/`pgnToken`, `CanonicalPgn.from` and the `ParsedGame` component order are used identically everywhere they appear. Visibility is settled once, in the task that creates each type: `ChesslibPgnParser` and `PgnTagReader` are public because they are used across package boundaries; `ValidatedMoves` and `IllegalMoveAtPly` stay package-private because nothing outside `chesslib` touches them.
