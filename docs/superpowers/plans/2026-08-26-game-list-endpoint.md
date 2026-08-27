# GET /games — list with filtering and pagination: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve `GET /api/games` — a paginated, filterable list of stored games — with the query's structure coming from a whitelist rather than from request strings.

**Architecture:** Query parameters bind into one validated record at the HTTP boundary and convert to a domain `GameQuery` of enums and typed fields. A dedicated `GameSearchQuery` in persistence turns that into a JPA Criteria query: predicates assembled from whichever filters are populated, ordering from a `GameSort` enum, and a matching count query. Rows come back as a `GameSummary` projection that never reads `movetext`. No application-layer class — the controller calls the domain repository port directly.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Jakarta Persistence 3.2 Criteria API, Hibernate 7.4.1, PostgreSQL 18, JUnit 5, AssertJ, Testcontainers.

**Spec:** [docs/superpowers/specs/2026-08-26-game-list-endpoint-design.md](../specs/2026-08-26-game-list-endpoint-design.md)

## Global Constraints

- **All work is in `services/core`.** There is no aggregator pom at the repository root; every Maven command below uses `-f services/core/pom.xml`.
- **No new dependencies and no migration.** Every filtered column and every usable index was created by `V2__create_games.sql`. `event` stays unindexed deliberately.
- **`GameEntity` never leaves `com.chessapp.game.persistence`.** It is package-private; the projection is mapped to domain types inside that package.
- **Domain code avoids Spring.** No `Pageable`, no `Sort`, no `Page` in `com.chessapp.game.domain`.
- **Only values bind; structure comes from enums.** Nothing derived from a request string may reach a query as SQL text.
- **Nulls sort last in both directions**, with a tie-break on `id` in the same direction.
- **Paging bounds:** `size` 1–100 (default 25), `page` 0–100 000 (default 0), `event` at most 255 characters.
- **Docker must be running** for every `*IT` test — they start a `postgres:18` Testcontainer.
- **Javadoc explains *why*, not *what*.** Match the density of the surrounding code; every non-obvious decision in this codebase carries its reason in a comment.
- Commit messages: imperative subject, blank line, body explaining the reasoning. Do not push.

## File Structure

| File | Responsibility |
| --- | --- |
| `domain/GameColour.java` | WHITE/BLACK — which side a player had |
| `domain/GameSort.java` | The sort whitelist, as a type |
| `domain/SortDirection.java` | ASC/DESC, free of Spring |
| `domain/GameQuery.java` | A validated search request |
| `domain/GameSummary.java` | A game as a list row — no `movetext` |
| `domain/GamePage.java` | One page plus the filtered total |
| `domain/GameRepository.java` | + `find(GameQuery)` |
| `persistence/LikePattern.java` | LIKE metacharacter escaping |
| `persistence/GameSearchQuery.java` | The Criteria query: predicates, ordering, count |
| `persistence/GameRepositoryAdapter.java` | + `find`, owns the transaction boundary |
| `api/GameListParams.java` | Bound and validated query parameters |
| `api/GameSummaryResponse.java` | A list row on the wire |
| `api/GamePageResponse.java` | The page envelope |
| `api/GameController.java` | + `GET /api/games` |

---

### Task 1: Domain query types

**Files:**
- Create: `services/core/src/main/java/com/chessapp/game/domain/GameColour.java`
- Create: `services/core/src/main/java/com/chessapp/game/domain/GameSort.java`
- Create: `services/core/src/main/java/com/chessapp/game/domain/SortDirection.java`
- Create: `services/core/src/main/java/com/chessapp/game/domain/GameQuery.java`
- Test: `services/core/src/test/java/com/chessapp/game/domain/GameQueryTest.java`

**Interfaces:**
- Consumes: `GameValues.required(T, String)` — package-private static in `com.chessapp.game.domain`, throws `IllegalArgumentException` when null.
- Produces: `GameQuery(UUID playerId, GameColour colour, GameResult result, LocalDate from, LocalDate to, String event, GameSort sort, SortDirection direction, int page, int size)`; enums `GameColour{WHITE,BLACK}`, `GameSort{PLAYED_ON}`, `SortDirection{ASC,DESC}`.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/com/chessapp/game/domain/GameQueryTest.java`:

```java
package com.chessapp.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameQueryTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private static GameQuery query(UUID playerId, GameColour colour,
                                   LocalDate from, LocalDate to, String event) {
        return new GameQuery(playerId, colour, null, from, to, event,
                GameSort.PLAYED_ON, SortDirection.DESC, 0, 25);
    }

    @Test
    void acceptsAQueryWithNoFiltersAtAll() {
        GameQuery query = query(null, null, null, null, null);

        assertThat(query.playerId()).isNull();
        assertThat(query.event()).isNull();
    }

    @Test
    void rejectsAColourWithNoPlayerToConstrain() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> query(null, GameColour.WHITE, null, null, null))
                .withMessageContaining("playerId");
    }

    @Test
    void acceptsAColourAlongsideAPlayer() {
        assertThat(query(PLAYER, GameColour.BLACK, null, null, null).colour())
                .isEqualTo(GameColour.BLACK);
    }

    @Test
    void rejectsAFromAfterItsTo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> query(null, null,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null))
                .withMessageContaining("from must not be after to");
    }

    /** The bounds are inclusive, so a single-day range is a legitimate request. */
    @Test
    void acceptsAFromEqualToItsTo() {
        LocalDate day = LocalDate.of(2026, 3, 14);

        assertThat(query(null, null, day, day, null).from()).isEqualTo(day);
    }

    @Test
    void trimsSurroundingWhitespaceFromTheEventFilter() {
        assertThat(query(null, null, null, null, "  championship  ").event())
                .isEqualTo("championship");
    }

    @Test
    void treatsABlankEventFilterAsNoFilter() {
        assertThat(query(null, null, null, null, "   ").event()).isNull();
    }

    /**
     * GameValues.optionalTag maps "?" to null because it is the PGN unknown marker.
     * A search term is not a tag value: someone looking for an event containing a
     * question mark must not silently get every game.
     */
    @Test
    void keepsAQuestionMarkAsASearchTermRatherThanTreatingItAsUnknown() {
        assertThat(query(null, null, null, null, "?").event()).isEqualTo("?");
    }

    @Test
    void rejectsANullSortRatherThanDefaultingIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameQuery(null, null, null, null, null, null,
                        null, SortDirection.DESC, 0, 25))
                .withMessageContaining("sort");
    }

    @Test
    void rejectsANullDirectionRatherThanDefaultingIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameQuery(null, null, null, null, null, null,
                        GameSort.PLAYED_ON, null, 0, 25))
                .withMessageContaining("direction");
    }

    @Test
    void rejectsANegativePage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameQuery(null, null, null, null, null, null,
                        GameSort.PLAYED_ON, SortDirection.DESC, -1, 25))
                .withMessageContaining("page");
    }

    @Test
    void rejectsAPageOfNoRows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameQuery(null, null, null, null, null, null,
                        GameSort.PLAYED_ON, SortDirection.DESC, 0, 0))
                .withMessageContaining("size");
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -f services/core/pom.xml test -Dtest=GameQueryTest`
Expected: compilation failure — `GameQuery`, `GameColour`, `GameSort` and `SortDirection` do not exist.

- [ ] **Step 3: Create the three enums**

`services/core/src/main/java/com/chessapp/game/domain/GameColour.java`:

```java
package com.chessapp.game.domain;

/**
 * Which colour a player had. Distinct from {@link GameSide}, which is one colour's
 * share of a particular game — this is the colour itself, used to narrow a search
 * to the games a player had White in, or Black.
 */
public enum GameColour {
    WHITE,
    BLACK
}
```

`services/core/src/main/java/com/chessapp/game/domain/GameSort.java`:

```java
package com.chessapp.game.domain;

/**
 * The columns a game list may be ordered by.
 *
 * <p>This enum is the whitelist. {@code ORDER BY} cannot be a bound parameter, so a
 * sort field arriving from a request and placed into a query is string
 * concatenation whatever it is called. Being an enum makes an unknown field
 * unrepresentable rather than merely rejected: conversion fails at the HTTP
 * boundary, before a query exists.
 *
 * <p>One value, because {@code played_on} is the only column with a supporting
 * index. Adding another is one constant here and one arm of the switch in
 * {@code GameSearchQuery} — which is the point of having the mechanism from the
 * start.
 */
public enum GameSort {
    PLAYED_ON
}
```

`services/core/src/main/java/com/chessapp/game/domain/SortDirection.java`:

```java
package com.chessapp.game.domain;

/**
 * Ascending or descending.
 *
 * <p>Deliberately not Spring Data's {@code Sort.Direction}: the domain stays free of
 * Spring. It also carries no null-handling of its own, because there is nothing to
 * choose — nulls sort last in both directions, and the query applies that.
 */
public enum SortDirection {
    ASC,
    DESC
}
```

- [ ] **Step 4: Create `GameQuery`**

`services/core/src/main/java/com/chessapp/game/domain/GameQuery.java`:

```java
package com.chessapp.game.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A validated request for one page of games.
 *
 * <p>Every filter is optional, and null means "does not constrain". Paging and
 * ordering are not optional: defaults are applied once, at the HTTP boundary by
 * {@code GameListParams}, so that two places cannot come to disagree about what
 * "no sort given" means. A null {@code sort} here is a defect, not a request for
 * the default.
 *
 * <p>The two cross-field rules are enforced here as well as by bean validation at
 * the boundary. Bean validation is the input gate; a rejection reaching this
 * constructor means a defect in that gate rather than bad input, and the invariant
 * still holds for any future caller that is not the controller.
 *
 * <p>{@code size} is floored at 1 but not capped: a page of no rows is meaningless
 * whoever asks for it, while an upper bound is resource protection against an
 * untrusted caller and belongs at the boundary that has one.
 */
public record GameQuery(UUID playerId,
                        GameColour colour,
                        GameResult result,
                        LocalDate from,
                        LocalDate to,
                        String event,
                        GameSort sort,
                        SortDirection direction,
                        int page,
                        int size) {

    public GameQuery {
        if (colour != null && playerId == null) {
            throw new IllegalArgumentException(
                    "colour narrows a player filter, so it requires playerId");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "from must not be after to, was: " + from + " to " + to);
        }
        event = searchTerm(event);
        sort = GameValues.required(sort, "sort");
        direction = GameValues.required(direction, "direction");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative, was: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least one row, was: " + size);
        }
    }

    /**
     * Blank and absent mean the same thing, so both become null: an empty filter
     * that reached the query would become the pattern {@code %%}, which matches
     * every game that has an event and silently drops every game that has none.
     *
     * <p>Deliberately not {@link GameValues#optionalTag}. That method maps
     * {@code "?"} to null because it is the PGN marker for an unknown tag value. A
     * search term is not a tag value, and someone looking for an event containing a
     * question mark must not be handed every game instead.
     */
    private static String searchTerm(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -f services/core/pom.xml test -Dtest=GameQueryTest`
Expected: PASS, 12 tests.

- [ ] **Step 6: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/domain/GameColour.java \
        services/core/src/main/java/com/chessapp/game/domain/GameSort.java \
        services/core/src/main/java/com/chessapp/game/domain/SortDirection.java \
        services/core/src/main/java/com/chessapp/game/domain/GameQuery.java \
        services/core/src/test/java/com/chessapp/game/domain/GameQueryTest.java
git commit -m "Add the domain query types for the game list

GameSort is the sort whitelist expressed as a type rather than as a rule
someone has to remember to apply: ORDER BY cannot be a bound parameter, so
an unknown sort field has to be unrepresentable rather than merely
rejected.

GameQuery re-checks the two cross-field rules that bean validation will
also enforce at the boundary, and normalises a blank event filter to null
so it cannot become the pattern that matches everything.

Issue: #8"
```

---

### Task 2: The list projection and its page

**Files:**
- Create: `services/core/src/main/java/com/chessapp/game/domain/GameSummary.java`
- Create: `services/core/src/main/java/com/chessapp/game/domain/GamePage.java`
- Test: `services/core/src/test/java/com/chessapp/game/domain/GamePageTest.java`

**Interfaces:**
- Consumes: `GameSide(UUID playerId, String name, Integer rating)`, `GameResult`, `GameSource`, `GameValues.required` — all in `com.chessapp.game.domain`.
- Produces: `GameSummary(UUID id, GameSide white, GameSide black, String event, String site, String round, LocalDate playedOn, GameResult result, String eco, GameSource source)`; `GamePage(List<GameSummary> content, int page, int size, long totalElements)`.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/com/chessapp/game/domain/GamePageTest.java`:

```java
package com.chessapp.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GamePageTest {

    private static GameSummary summary() {
        return new GameSummary(UUID.randomUUID(),
                new GameSide(UUID.randomUUID(), "Green, Guy", 1850),
                new GameSide(UUID.randomUUID(), "Club Opponent", null),
                "Club Championship", "London ENG", "3",
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "C60", GameSource.PGN_IMPORT);
    }

    @Test
    void carriesTheFilteredTotalAlongsideTheRowsOfOnePage() {
        GamePage page = new GamePage(List.of(summary()), 2, 25, 143);

        assertThat(page.content()).hasSize(1);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(25);
        assertThat(page.totalElements()).isEqualTo(143);
    }

    /**
     * The caller's list is copied, not adopted. A page handed to the API layer is a
     * value, and a mutation of the list it was built from must not change it.
     */
    @Test
    void copiesTheContentItIsGiven() {
        List<GameSummary> mutable = new ArrayList<>(List.of(summary()));

        GamePage page = new GamePage(mutable, 0, 25, 1);
        mutable.clear();

        assertThat(page.content()).hasSize(1);
    }

    @Test
    void rejectsNullContentRatherThanCarryingIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GamePage(null, 0, 25, 0))
                .withMessageContaining("content");
    }

    @Test
    void isEmptyRatherThanNullWhenNothingMatched() {
        GamePage page = new GamePage(List.of(), 0, 25, 0);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void rejectsASummaryWithNoIdentity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameSummary(null,
                        new GameSide(UUID.randomUUID(), "Green, Guy", null),
                        new GameSide(UUID.randomUUID(), "Club Opponent", null),
                        null, null, null, null, GameResult.DRAW, null, GameSource.PERSONAL))
                .withMessageContaining("id");
    }

    @Test
    void refusesToBeModifiedThroughTheListItExposes() {
        GamePage page = new GamePage(List.of(summary()), 0, 25, 1);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> page.content().clear());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -f services/core/pom.xml test -Dtest=GamePageTest`
Expected: compilation failure — `GameSummary` and `GamePage` do not exist.

- [ ] **Step 3: Create `GameSummary`**

`services/core/src/main/java/com/chessapp/game/domain/GameSummary.java`:

```java
package com.chessapp.game.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A game as a row of a list: everything {@link Game} carries except the moves.
 *
 * <p>{@code movetext} is absent by design rather than dropped later by a mapper.
 * It is a {@code TEXT} column, so a longer game's value is stored out of line and
 * reading it costs a separate fetch; selecting it for every row of every page would
 * pay that cost repeatedly to render a table that shows none of it. Because the
 * type has no field for it, the query cannot select it by accident.
 *
 * <p>{@code sourcePgn} is absent for ADR 0002's reason: it is provenance, and
 * nothing reads it to answer a product question.
 *
 * <p>Holds a {@link GameSide} per colour rather than six flat fields, so a summary
 * and a {@code Game} read the same way. That is why the projection is assembled
 * from a {@code Tuple} in persistence: the Jakarta Persistence specification does
 * not allow a compound selection as an argument to another, so a nested
 * {@code construct} for the two sides is not portable.
 *
 * <p>Only presence is checked. A summary is built from a row that was validated on
 * the way in, and the projection has no {@code movetext} to check the rules that
 * matter most.
 */
public record GameSummary(UUID id,
                          GameSide white,
                          GameSide black,
                          String event,
                          String site,
                          String round,
                          LocalDate playedOn,
                          GameResult result,
                          String eco,
                          GameSource source) {

    public GameSummary {
        if (id == null) {
            throw new IllegalArgumentException("id is required; a GameSummary is always persisted");
        }
        white = GameValues.required(white, "white");
        black = GameValues.required(black, "black");
        result = GameValues.required(result, "result");
        source = GameValues.required(source, "source");
    }
}
```

- [ ] **Step 4: Create `GamePage`**

`services/core/src/main/java/com/chessapp/game/domain/GamePage.java`:

```java
package com.chessapp.game.domain;

import java.util.List;

/**
 * One page of a game search, plus the size of the whole filtered set.
 *
 * <p>{@code totalElements} counts every game matching the filters rather than the
 * rows returned, so a caller can render a pager. It is the only aggregate here:
 * total pages is presentation arithmetic and belongs to the response type.
 *
 * <p>The rows and the total come from two statements. Under PostgreSQL's default
 * {@code READ COMMITTED} isolation each takes its own snapshot, so a concurrent
 * write can leave them momentarily disagreeing. That is accepted — see the design's
 * known limitations — and no read-only transaction closes it.
 */
public record GamePage(List<GameSummary> content, int page, int size, long totalElements) {

    public GamePage {
        content = List.copyOf(GameValues.required(content, "content"));
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -f services/core/pom.xml test -Dtest=GamePageTest`
Expected: PASS, 6 tests.

- [ ] **Step 6: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/domain/GameSummary.java \
        services/core/src/main/java/com/chessapp/game/domain/GamePage.java \
        services/core/src/test/java/com/chessapp/game/domain/GamePageTest.java
git commit -m "Add the game list projection and its page

GameSummary has no movetext field, so a list query cannot select the
column by accident. movetext is stored out of line for longer games, and
reading it per row to render a table that shows none of it is the cost the
projection exists to avoid.

GamePage copies the content it is given and carries the filtered total
rather than the returned row count.

Issue: #8"
```

---

### Task 3: LIKE metacharacter escaping

**Files:**
- Create: `services/core/src/main/java/com/chessapp/game/persistence/LikePattern.java`
- Test: `services/core/src/test/java/com/chessapp/game/persistence/LikePatternTest.java`

**Interfaces:**
- Produces: package-private `LikePattern.containing(String) -> String` and `LikePattern.ESCAPE` (a `char`), both in `com.chessapp.game.persistence`.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/com/chessapp/game/persistence/LikePatternTest.java`:

```java
package com.chessapp.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternTest {

    @Test
    void wrapsAnOrdinaryTermInWildcardsSoItMatchesASubstring() {
        assertThat(LikePattern.containing("championship")).isEqualTo("%championship%");
    }

    @Test
    void foldsCaseSoTheComparisonIsInsensitive() {
        assertThat(LikePattern.containing("Championship")).isEqualTo("%championship%");
    }

    /**
     * Unescaped, "50%" would match anything containing "50" — including "500 Club".
     * The value still binds, so this is not injection; the filter would simply
     * answer a different question from the one asked.
     */
    @Test
    void escapesPerCentSoItMatchesLiterally() {
        assertThat(LikePattern.containing("50%")).isEqualTo("%50\\%%");
    }

    /** Unescaped, "_" is a single-character wildcard and matches every one-character value. */
    @Test
    void escapesUnderscoreSoItMatchesLiterally() {
        assertThat(LikePattern.containing("a_b")).isEqualTo("%a\\_b%");
    }

    @Test
    void escapesTheEscapeCharacterItself() {
        assertThat(LikePattern.containing("c:\\path")).isEqualTo("%c:\\\\path%");
    }

    /**
     * A backslash followed by a per cent is two literal characters in the input and
     * must become two escapes. Escaping with successive replacements in the wrong
     * order produces a single escape here, which is the classic way this is got
     * wrong; a single pass over the characters cannot express the bug.
     */
    @Test
    void escapesEachCharacterOfAnAlreadyEscapedLookingSequence() {
        assertThat(LikePattern.containing("\\%")).isEqualTo("%\\\\\\%%");
    }

    @Test
    void producesAMatchEverythingPatternForAnEmptyTerm() {
        assertThat(LikePattern.containing("")).isEqualTo("%%");
    }
}
```

The last test documents behaviour rather than endorsing it: `GameQuery` never passes an empty term, because it normalises blank to null.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -f services/core/pom.xml test -Dtest=LikePatternTest`
Expected: compilation failure — `LikePattern` does not exist.

- [ ] **Step 3: Write the implementation**

`services/core/src/main/java/com/chessapp/game/persistence/LikePattern.java`:

```java
package com.chessapp.game.persistence;

import java.util.Locale;

/**
 * Turns a search term into a SQL {@code LIKE} pattern matching it as a literal
 * substring.
 *
 * <p>{@code %} and {@code _} are metacharacters inside a pattern. Left alone, a
 * search for {@code _} matches every single-character value and a {@code %} in the
 * term matches anything. The term still binds as a parameter, so this is not
 * injection — the filter would simply answer a different question from the one the
 * user asked.
 *
 * <p>Escaping is a single pass over the characters rather than successive
 * {@code String.replace} calls. Replacing {@code %} and {@code _} before the
 * backslash would then escape the backslashes just inserted, turning every escape
 * into a literal backslash followed by an unescaped metacharacter. A single pass
 * cannot express that bug, so the ordering hazard is removed rather than
 * documented.
 *
 * <p>Folded with {@link Locale#ROOT} so the result does not depend on the server's
 * default locale. The column is folded separately, by SQL {@code lower()}.
 */
final class LikePattern {

    /** Must be the escape character passed to {@code CriteriaBuilder.like}. */
    static final char ESCAPE = '\\';

    private LikePattern() {
    }

    /** A pattern matching any value containing {@code term}, ignoring case. */
    static String containing(String term) {
        return "%" + escape(term.toLowerCase(Locale.ROOT)) + "%";
    }

    private static String escape(String term) {
        StringBuilder escaped = new StringBuilder(term.length());
        for (int index = 0; index < term.length(); index++) {
            char character = term.charAt(index);
            if (character == ESCAPE || character == '%' || character == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `mvn -f services/core/pom.xml test -Dtest=LikePatternTest`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/persistence/LikePattern.java \
        services/core/src/test/java/com/chessapp/game/persistence/LikePatternTest.java
git commit -m "Escape LIKE metacharacters in the event search term

% and _ are metacharacters inside a pattern, so an unescaped term answers
a different question from the one asked: a search for _ matches every
single-character event.

Escaping is one pass over the characters rather than three replacements,
because replacing % and _ before the backslash escapes the backslashes
just inserted. A single pass cannot express that bug.

Issue: #8"
```

---

### Task 4: The search query and the repository method

**Files:**
- Create: `services/core/src/main/java/com/chessapp/game/persistence/GameSearchQuery.java`
- Modify: `services/core/src/main/java/com/chessapp/game/domain/GameRepository.java`
- Modify: `services/core/src/main/java/com/chessapp/game/persistence/GameRepositoryAdapter.java`
- Test: `services/core/src/test/java/com/chessapp/game/persistence/GameSearchIT.java`

**Interfaces:**
- Consumes: `GameQuery`, `GameSummary`, `GamePage`, `GameColour`, `GameSort`, `SortDirection` (Task 1 and 2); `LikePattern.containing`, `LikePattern.ESCAPE` (Task 3); the existing package-private `GameEntity` with attributes `id`, `whitePlayerId`, `whiteName`, `whiteRating`, `blackPlayerId`, `blackName`, `blackRating`, `event`, `site`, `round`, `playedOn`, `result`, `eco`, `source`.
- Produces: `GameRepository.find(GameQuery) -> GamePage`.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/com/chessapp/game/persistence/GameSearchIT.java`:

```java
package com.chessapp.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.game.domain.GameColour;
import com.chessapp.game.domain.GamePage;
import com.chessapp.game.domain.GameQuery;
import com.chessapp.game.domain.GameRepository;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSort;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.GameSummary;
import com.chessapp.game.domain.NewGame;
import com.chessapp.game.domain.SortDirection;
import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.PlayerRepository;
import java.time.LocalDate;
import java.util.List;
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
 * The container is shared across the class with no cleanup between methods, so an
 * unfiltered query would see every game every other test created. Each test
 * therefore gets its own pair of players in {@link #createPlayers()} and scopes
 * every query to them.
 */
@Testcontainers
@SpringBootTest
class GameSearchIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private GameRepository games;

    @Autowired
    private PlayerRepository players;

    private UUID subject;
    private UUID opponent;

    @BeforeEach
    void createPlayers() {
        String unique = UUID.randomUUID().toString();
        subject = players.createOrFind(new NewPlayer("Subject " + unique, null, null)).id();
        opponent = players.createOrFind(new NewPlayer("Opponent " + unique, null, null)).id();
    }

    private UUID store(UUID whitePlayerId, UUID blackPlayerId,
                       LocalDate playedOn, GameResult result, String event) {
        return games.save(new NewGame(
                new GameSide(whitePlayerId, "White " + whitePlayerId, null),
                new GameSide(blackPlayerId, "Black " + blackPlayerId, null),
                event, null, null, playedOn, result, null, GameSource.PGN_IMPORT,
                "1. e4 e5", null)).id();
    }

    private UUID storeDated(LocalDate playedOn) {
        return store(subject, opponent, playedOn, GameResult.DRAW, null);
    }

    private GameQuery filtered(GameColour colour, GameResult result,
                               LocalDate from, LocalDate to, String event) {
        return new GameQuery(subject, colour, result, from, to, event,
                GameSort.PLAYED_ON, SortDirection.DESC, 0, 50);
    }

    private GameQuery all() {
        return filtered(null, null, null, null, null);
    }

    private GameQuery sorted(SortDirection direction) {
        return new GameQuery(subject, null, null, null, null, null,
                GameSort.PLAYED_ON, direction, 0, 50);
    }

    private GameQuery paged(int page, int size) {
        return new GameQuery(subject, null, null, null, null, null,
                GameSort.PLAYED_ON, SortDirection.DESC, page, size);
    }

    private static List<UUID> ids(GamePage page) {
        return page.content().stream().map(GameSummary::id).toList();
    }

    @Test
    void aPlayerFilterAloneMatchesGamesOfEitherColour() {
        UUID asWhite = store(subject, opponent, LocalDate.of(2026, 1, 1), GameResult.DRAW, null);
        UUID asBlack = store(opponent, subject, LocalDate.of(2026, 2, 1), GameResult.DRAW, null);

        assertThat(ids(games.find(all()))).containsExactlyInAnyOrder(asWhite, asBlack);
    }

    @Test
    void aColourNarrowsThePlayerFilterToOneSide() {
        UUID asWhite = store(subject, opponent, LocalDate.of(2026, 1, 1), GameResult.DRAW, null);
        store(opponent, subject, LocalDate.of(2026, 2, 1), GameResult.DRAW, null);

        assertThat(ids(games.find(filtered(GameColour.WHITE, null, null, null, null))))
                .containsExactly(asWhite);
    }

    @Test
    void filtersToOneResult() {
        UUID drawn = store(subject, opponent, LocalDate.of(2026, 1, 1), GameResult.DRAW, null);
        store(subject, opponent, LocalDate.of(2026, 2, 1), GameResult.WHITE_WON, null);

        assertThat(ids(games.find(filtered(null, GameResult.DRAW, null, null, null))))
                .containsExactly(drawn);
    }

    @Test
    void treatsBothDateBoundsAsInclusive() {
        UUID first = storeDated(LocalDate.of(2026, 1, 1));
        UUID last = storeDated(LocalDate.of(2026, 6, 30));
        storeDated(LocalDate.of(2025, 12, 31));
        storeDated(LocalDate.of(2026, 7, 1));

        GamePage page = games.find(filtered(null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), null));

        assertThat(ids(page)).containsExactlyInAnyOrder(first, last);
    }

    /**
     * played_on is null whenever a PGN date was only partly known, and no comparison
     * is true of null. A user filtering to 2026 therefore does not see a game that
     * said "2026.??.??" — correct, and exactly the kind of thing reported as a bug,
     * so it is asserted rather than assumed.
     */
    @Test
    void excludesAnUndatedGameFromAnyDateBound() {
        UUID dated = storeDated(LocalDate.of(2026, 3, 14));
        storeDated(null);

        assertThat(ids(games.find(filtered(null, null, LocalDate.of(2026, 1, 1), null, null))))
                .containsExactly(dated);
    }

    @Test
    void matchesAnEventSubstringIgnoringCase() {
        UUID matching = store(subject, opponent, null, GameResult.DRAW, "Obs Club Championship");
        store(subject, opponent, null, GameResult.DRAW, "Kent Open");

        assertThat(ids(games.find(filtered(null, null, null, null, "CHAMPIONSHIP"))))
                .containsExactly(matching);
    }

    /** lower(null) is null and null LIKE anything is null, so an unset event never matches. */
    @Test
    void doesNotMatchAGameWithNoEventAtAll() {
        store(subject, opponent, null, GameResult.DRAW, null);

        assertThat(games.find(filtered(null, null, null, null, "open")).content()).isEmpty();
    }

    @Test
    void matchesAPerCentInAnEventLiterally() {
        UUID literal = store(subject, opponent, null, GameResult.DRAW, "50% Club");
        store(subject, opponent, null, GameResult.DRAW, "500 Club");

        assertThat(ids(games.find(filtered(null, null, null, null, "50%"))))
                .containsExactly(literal);
    }

    @Test
    void matchesAnUnderscoreInAnEventLiterally() {
        UUID literal = store(subject, opponent, null, GameResult.DRAW, "A_B Open");
        store(subject, opponent, null, GameResult.DRAW, "AxB Open");

        assertThat(ids(games.find(filtered(null, null, null, null, "A_B"))))
                .containsExactly(literal);
    }

    @Test
    void combinesEveryFilterWithAnd() {
        UUID wanted = store(subject, opponent,
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "Obs Club Championship");
        store(opponent, subject,
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "Obs Club Championship");
        store(subject, opponent,
                LocalDate.of(2026, 3, 14), GameResult.DRAW, "Obs Club Championship");
        store(subject, opponent,
                LocalDate.of(2020, 3, 14), GameResult.WHITE_WON, "Obs Club Championship");
        store(subject, opponent,
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "Kent Open");

        GamePage page = games.find(filtered(GameColour.WHITE, GameResult.WHITE_WON,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "championship"));

        assertThat(ids(page)).containsExactly(wanted);
    }

    /**
     * PostgreSQL sorts nulls first under DESC, and the indexes are declared
     * DESC NULLS LAST precisely so undated games do not lead the list.
     */
    @Test
    void sortsAnUndatedGameLastWhenDescending() {
        storeDated(LocalDate.of(2026, 1, 1));
        storeDated(LocalDate.of(2026, 6, 1));
        UUID undated = storeDated(null);

        assertThat(ids(games.find(sorted(SortDirection.DESC)))).endsWith(undated);
    }

    /**
     * Nulls last in both directions is a deliberate choice: reversing the order must
     * not make undated games jump from the bottom of the list to the top. It costs a
     * sort, because a backwards scan of a DESC NULLS LAST index yields nulls first.
     */
    @Test
    void sortsAnUndatedGameLastWhenAscendingToo() {
        storeDated(LocalDate.of(2026, 1, 1));
        storeDated(LocalDate.of(2026, 6, 1));
        UUID undated = storeDated(null);

        assertThat(ids(games.find(sorted(SortDirection.ASC)))).endsWith(undated);
    }

    @Test
    void ordersDatedGamesMostRecentFirstWhenDescending() {
        UUID older = storeDated(LocalDate.of(2026, 1, 1));
        UUID newer = storeDated(LocalDate.of(2026, 6, 1));

        assertThat(ids(games.find(sorted(SortDirection.DESC)))).containsExactly(newer, older);
    }

    /**
     * Without a tie-break, games sharing a played_on have no defined relative order
     * and paging across them can repeat one row while skipping another.
     */
    @Test
    void pagesAcrossGamesSharingADateWithoutRepeatingOrSkippingAny() {
        LocalDate sameDay = LocalDate.of(2026, 3, 14);
        UUID first = storeDated(sameDay);
        UUID second = storeDated(sameDay);
        UUID third = storeDated(sameDay);

        List<UUID> seen = List.copyOf(ids(games.find(paged(0, 2))));
        List<UUID> next = ids(games.find(paged(1, 2)));

        assertThat(seen).hasSize(2);
        assertThat(next).hasSize(1);
        assertThat(seen).doesNotContainAnyElementsOf(next);
        assertThat(List.of(seen, next).stream().flatMap(List::stream).toList())
                .containsExactlyInAnyOrder(first, second, third);
    }

    @Test
    void countsTheFilteredSetRatherThanTheReturnedPage() {
        storeDated(LocalDate.of(2026, 1, 1));
        storeDated(LocalDate.of(2026, 2, 1));
        storeDated(LocalDate.of(2026, 3, 1));

        GamePage page = games.find(paged(0, 2));

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    void returnsAnEmptyPagePastTheEndWhileStillReportingTheTotal() {
        storeDated(LocalDate.of(2026, 1, 1));

        GamePage page = games.find(paged(9, 25));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(9);
    }

    @Test
    void returnsEveryProjectedFieldOfARow() {
        store(subject, opponent, LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "Obs Club");

        GameSummary row = games.find(all()).content().getFirst();

        assertThat(row.id()).isNotNull();
        assertThat(row.white().playerId()).isEqualTo(subject);
        assertThat(row.white().name()).isEqualTo("White " + subject);
        assertThat(row.black().playerId()).isEqualTo(opponent);
        assertThat(row.event()).isEqualTo("Obs Club");
        assertThat(row.playedOn()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(row.result()).isEqualTo(GameResult.WHITE_WON);
        assertThat(row.source()).isEqualTo(GameSource.PGN_IMPORT);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Ensure Docker is running, then run: `mvn -f services/core/pom.xml verify -Dit.test=GameSearchIT`
Expected: compilation failure — `GameRepository.find` does not exist.

- [ ] **Step 3: Add the repository method**

In `services/core/src/main/java/com/chessapp/game/domain/GameRepository.java`, add the method after `findById`. No new imports: `GamePage` and `GameQuery` are in this same package.

```java
    /**
     * One page of games matching the query, plus the size of the whole filtered
     * set.
     *
     * <p>Returns an empty page rather than an empty {@code Optional}: the collection
     * always exists, and it is the selection that can be empty.
     */
    GamePage find(GameQuery query);
```

- [ ] **Step 4: Write `GameSearchQuery`**

`services/core/src/main/java/com/chessapp/game/persistence/GameSearchQuery.java`:

```java
package com.chessapp.game.persistence;

import com.chessapp.game.domain.GameColour;
import com.chessapp.game.domain.GamePage;
import com.chessapp.game.domain.GameQuery;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.GameSummary;
import com.chessapp.game.domain.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one query in this application whose shape varies with the request.
 *
 * <p>Everything else runs a fixed statement binding every value as a named
 * parameter. A list endpoint cannot: which {@code WHERE} clauses exist depends on
 * which filters were supplied, and {@code ORDER BY} cannot be a bound parameter at
 * all. So only values bind here, and the structure comes from the enums and typed
 * fields of {@link GameQuery} — never from a request string.
 *
 * <p>Written as Criteria rather than as native SQL with assembled fragments,
 * because assembled fragments are structural string concatenation however carefully
 * whitelisted, and because two hand-written statements — rows and count — drift.
 * Spring Data {@code Specification} was the alternative; it was declined so that
 * the null precedence below is stated here rather than delegated to a translation
 * that would have to be verified.
 */
@Component
class GameSearchQuery {

    private final EntityManager entityManager;

    GameSearchQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    GamePage run(GameQuery query) {
        return new GamePage(rows(query), query.page(), query.size(), total(query));
    }

    /**
     * Projects the summary columns rather than the entity. {@code movetext} is
     * stored out of line for longer games, so selecting it for every row of every
     * page would cost a fetch per row to render a table that shows none of it.
     *
     * <p>Selected as a {@code Tuple} and mapped by hand: {@link GameSummary} holds a
     * {@link GameSide} per colour, and the specification does not allow a compound
     * selection as an argument to another, so a nested {@code construct} is not
     * portable.
     */
    private List<GameSummary> rows(GameQuery query) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> criteria = builder.createTupleQuery();
        Root<GameEntity> game = criteria.from(GameEntity.class);

        criteria.multiselect(game.get("id"),
                        game.get("whitePlayerId"), game.get("whiteName"), game.get("whiteRating"),
                        game.get("blackPlayerId"), game.get("blackName"), game.get("blackRating"),
                        game.get("event"), game.get("site"), game.get("round"),
                        game.get("playedOn"), game.get("result"), game.get("eco"),
                        game.get("source"))
                .where(filters(builder, game, query))
                .orderBy(ordering(builder, game, query));

        return entityManager.createQuery(criteria)
                .setFirstResult(query.page() * query.size())
                .setMaxResults(query.size())
                .getResultList()
                .stream()
                .map(GameSearchQuery::toSummary)
                .toList();
    }

    private long total(GameQuery query) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
        Root<GameEntity> game = criteria.from(GameEntity.class);

        criteria.select(builder.count(game)).where(filters(builder, game, query));

        return entityManager.createQuery(criteria).getSingleResult();
    }

    /**
     * Called once per query with that query's own {@link Root}. A Criteria
     * {@link Predicate} belongs to the root that produced it, so the rows query and
     * the count query cannot share instances — sharing this method is what stops
     * their filtering from drifting, and a rows query and a count query that
     * disagree are a silently wrong answer rather than a failure.
     */
    private static Predicate[] filters(CriteriaBuilder builder, Root<GameEntity> game,
                                       GameQuery query) {
        List<Predicate> filters = new ArrayList<>();

        UUID playerId = query.playerId();
        if (playerId != null) {
            filters.add(playerFilter(builder, game, playerId, query.colour()));
        }
        GameResult result = query.result();
        if (result != null) {
            filters.add(builder.equal(game.get("result"), result));
        }
        LocalDate from = query.from();
        if (from != null) {
            filters.add(builder.greaterThanOrEqualTo(game.get("playedOn"), from));
        }
        LocalDate to = query.to();
        if (to != null) {
            filters.add(builder.lessThanOrEqualTo(game.get("playedOn"), to));
        }
        String event = query.event();
        if (event != null) {
            filters.add(builder.like(builder.lower(game.get("event")),
                    LikePattern.containing(event), LikePattern.ESCAPE));
        }
        return filters.toArray(new Predicate[0]);
    }

    /**
     * Without a colour this is an OR across both player columns, which PostgreSQL
     * serves as a BitmapOr over {@code games_white_player_played_on_idx} and
     * {@code games_black_player_played_on_idx} rather than as a scan.
     */
    private static Predicate playerFilter(CriteriaBuilder builder, Root<GameEntity> game,
                                          UUID playerId, GameColour colour) {
        Predicate asWhite = builder.equal(game.get("whitePlayerId"), playerId);
        Predicate asBlack = builder.equal(game.get("blackPlayerId"), playerId);
        return switch (colour) {
            case null -> builder.or(asWhite, asBlack);
            case WHITE -> asWhite;
            case BLACK -> asBlack;
        };
    }

    /**
     * Nulls last in both directions. {@code played_on} is null whenever a PGN date
     * was only partly known, and PostgreSQL sorts nulls first under {@code DESC}, so
     * the default would put every undated game ahead of the most recent dated one.
     * The indexes are declared {@code DESC NULLS LAST} for that reason, and
     * descending therefore matches {@code games_played_on_idx} exactly. Ascending
     * cannot be served by it — a backwards scan yields nulls first — and pays for a
     * sort instead, so that reversing the order does not send undated games to the
     * top of the list.
     *
     * <p>The tie-break is not decoration. Without it two games sharing a
     * {@code played_on} have no defined relative order, and paging across them can
     * repeat one row while skipping another. {@code id} is a {@code uuidv7()}, so it
     * is time-ordered and reads as "most recently imported first" under
     * {@code DESC}.
     */
    private static List<Order> ordering(CriteriaBuilder builder, Root<GameEntity> game,
                                        GameQuery query) {
        String attribute = switch (query.sort()) {
            case PLAYED_ON -> "playedOn";
        };
        return List.of(order(builder, game.get(attribute), query.direction()),
                order(builder, game.get("id"), query.direction()));
    }

    private static Order order(CriteriaBuilder builder, Path<?> path, SortDirection direction) {
        return switch (direction) {
            case ASC -> builder.asc(path, Nulls.LAST);
            case DESC -> builder.desc(path, Nulls.LAST);
        };
    }

    /** Positional, matching the {@code multiselect} order above. */
    private static GameSummary toSummary(Tuple row) {
        return new GameSummary(row.get(0, UUID.class),
                new GameSide(row.get(1, UUID.class), row.get(2, String.class),
                        row.get(3, Integer.class)),
                new GameSide(row.get(4, UUID.class), row.get(5, String.class),
                        row.get(6, Integer.class)),
                row.get(7, String.class),
                row.get(8, String.class),
                row.get(9, String.class),
                row.get(10, LocalDate.class),
                row.get(11, GameResult.class),
                row.get(12, String.class),
                row.get(13, GameSource.class));
    }
}
```

- [ ] **Step 5: Wire it into the adapter**

In `services/core/src/main/java/com/chessapp/game/persistence/GameRepositoryAdapter.java`, add the imports `com.chessapp.game.domain.GamePage` and `com.chessapp.game.domain.GameQuery`, take the new collaborator in the constructor, and add the method:

```java
    private final GameJpaRepository jpa;
    private final GameSearchQuery search;

    GameRepositoryAdapter(GameJpaRepository jpa, GameSearchQuery search) {
        this.jpa = jpa;
        this.search = search;
    }
```

```java
    /**
     * {@code readOnly} for one connection checkout instead of two and to keep
     * Hibernate from dirty-checking a read — and explicitly <em>not</em> for
     * consistency between the two statements it covers. Under PostgreSQL's default
     * {@code READ COMMITTED} isolation each statement takes its own snapshot, so a
     * concurrent write can still leave the rows and the total disagreeing. Closing
     * that would need repeatable-read isolation, and nothing yet needs it.
     */
    @Override
    @Transactional(readOnly = true)
    public GamePage find(GameQuery query) {
        return search.run(query);
    }
```

- [ ] **Step 6: Run the test and confirm it passes**

Run: `mvn -f services/core/pom.xml verify -Dit.test=GameSearchIT`
Expected: PASS, 17 tests.

If ordering assertions fail with undated games appearing first, the null precedence is not reaching SQL — check that `jakarta.persistence.criteria.Nulls` is imported and the two-argument `builder.asc`/`builder.desc` overloads are being called, not the one-argument ones.

- [ ] **Step 7: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/persistence/GameSearchQuery.java \
        services/core/src/main/java/com/chessapp/game/domain/GameRepository.java \
        services/core/src/main/java/com/chessapp/game/persistence/GameRepositoryAdapter.java \
        services/core/src/test/java/com/chessapp/game/persistence/GameSearchIT.java
git commit -m "Search games by their metadata, paged and ordered

The first query in this application whose shape varies with the request.
Only values bind; which predicates exist and which column and direction
the ordering uses come from GameQuery's enums and typed fields.

Predicates are constructed once per query root rather than shared, because
a Criteria Predicate belongs to the root that produced it — but both the
rows query and the count query get their structure from the same method,
which is what stops a silently wrong total.

Nulls sort last in both directions, so reversing the order does not send
undated games to the top of the list, and the id tie-break keeps paging
stable across games sharing a date.

Issue: #8"
```

---

### Task 5: Query parameter binding and validation

**Files:**
- Create: `services/core/src/main/java/com/chessapp/game/api/GameListParams.java`
- Test: `services/core/src/test/java/com/chessapp/game/api/GameListParamsTest.java`

**Interfaces:**
- Consumes: `GameQuery`, `GameColour`, `GameSort`, `SortDirection`, `GameResult` (Task 1).
- Produces: `GameListParams(UUID playerId, GameColour colour, GameResult result, LocalDate from, LocalDate to, String event, GameSort sort, SortDirection direction, Integer page, Integer size)` with `toQuery() -> GameQuery`.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/com/chessapp/game/api/GameListParamsTest.java`:

```java
package com.chessapp.game.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.game.domain.GameColour;
import com.chessapp.game.domain.GameQuery;
import com.chessapp.game.domain.GameSort;
import com.chessapp.game.domain.SortDirection;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs a real {@link Validator} rather than calling the {@code @AssertTrue} methods
 * directly, so the test proves the annotations are wired rather than that the
 * predicates are correct in isolation.
 */
class GameListParamsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    private static GameListParams params(UUID playerId, GameColour colour,
                                         LocalDate from, LocalDate to, String event,
                                         Integer page, Integer size) {
        return new GameListParams(playerId, colour, null, from, to, event,
                null, null, page, size);
    }

    private static Set<String> messages(GameListParams params) {
        return validator.validate(params).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void appliesTheDocumentedDefaultsWhenNothingWasSupplied() {
        GameListParams params = params(null, null, null, null, null, null, null);

        assertThat(params.sort()).isEqualTo(GameSort.PLAYED_ON);
        assertThat(params.direction()).isEqualTo(SortDirection.DESC);
        assertThat(params.page()).isZero();
        assertThat(params.size()).isEqualTo(25);
    }

    @Test
    void keepsSuppliedPagingAndSortingOverTheDefaults() {
        GameListParams params = new GameListParams(null, null, null, null, null, null,
                GameSort.PLAYED_ON, SortDirection.ASC, 3, 10);

        assertThat(params.direction()).isEqualTo(SortDirection.ASC);
        assertThat(params.page()).isEqualTo(3);
        assertThat(params.size()).isEqualTo(10);
    }

    @Test
    void trimsSurroundingWhitespaceFromTheEventFilter() {
        assertThat(params(null, null, null, null, "  championship ", null, null).event())
                .isEqualTo("championship");
    }

    /**
     * A blank filter reaching the query would become the pattern that matches every
     * game with an event and drops every game without one — a filter the user
     * believes they cleared, quietly removing rows.
     */
    @Test
    void treatsABlankEventFilterAsNoFilter() {
        assertThat(params(null, null, null, null, "   ", null, null).event()).isNull();
    }

    @Test
    void acceptsAFullyPopulatedRequest() {
        UUID player = UUID.randomUUID();

        assertThat(messages(params(player, GameColour.WHITE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "open", 0, 25)))
                .isEmpty();
    }

    @Test
    void rejectsAColourWithNoPlayerToNarrow() {
        assertThat(messages(params(null, GameColour.WHITE, null, null, null, null, null)))
                .contains("colour requires playerId");
    }

    @Test
    void rejectsAFromAfterItsTo() {
        assertThat(messages(params(null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null, null, null)))
                .contains("from must not be after to");
    }

    @Test
    void rejectsAPageSizeBeyondTheCap() {
        assertThat(messages(params(null, null, null, null, null, 0, 500))).isNotEmpty();
    }

    @Test
    void rejectsAPageOfNoRows() {
        assertThat(messages(params(null, null, null, null, null, 0, 0))).isNotEmpty();
    }

    @Test
    void rejectsANegativePage() {
        assertThat(messages(params(null, null, null, null, null, -1, 25))).isNotEmpty();
    }

    /**
     * setFirstResult takes an int, so an unbounded page overflows page * size to a
     * negative and becomes a 500 rather than a rejected request.
     */
    @Test
    void rejectsAPageBeyondTheOverflowBound() {
        assertThat(messages(params(null, null, null, null, null, 100_001, 25))).isNotEmpty();
    }

    @Test
    void rejectsAnEventTermBeyondTheLengthCap() {
        assertThat(messages(params(null, null, null, null, "x".repeat(256), null, null)))
                .isNotEmpty();
    }

    @Test
    void convertsToADomainQueryCarryingEveryValue() {
        UUID player = UUID.randomUUID();

        GameQuery query = new GameListParams(player, GameColour.BLACK, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), " open ",
                GameSort.PLAYED_ON, SortDirection.ASC, 2, 10).toQuery();

        assertThat(query.playerId()).isEqualTo(player);
        assertThat(query.colour()).isEqualTo(GameColour.BLACK);
        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(query.to()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(query.event()).isEqualTo("open");
        assertThat(query.direction()).isEqualTo(SortDirection.ASC);
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(10);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -f services/core/pom.xml test -Dtest=GameListParamsTest`
Expected: compilation failure — `GameListParams` does not exist.

- [ ] **Step 3: Write the implementation**

`services/core/src/main/java/com/chessapp/game/api/GameListParams.java`:

```java
package com.chessapp.game.api;

import com.chessapp.game.domain.GameColour;
import com.chessapp.game.domain.GameQuery;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSort;
import com.chessapp.game.domain.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * The query parameters of {@code GET /api/games}, bound as one record so the two
 * cross-field rules have a single home and fail the same way the single-field ones
 * do.
 *
 * <p>Every enum-typed parameter is its own whitelist: an unknown {@code sort},
 * {@code direction}, {@code colour} or {@code result} fails in conversion, before a
 * query is built, and becomes a 400 through the problem-details handler.
 *
 * <p>Paging and sorting are boxed so an omitted parameter binds to null and this
 * constructor can apply the default. Defaults live here and nowhere else, so that
 * two places cannot come to disagree about what "no sort given" means —
 * {@link GameQuery} requires them and does not default them.
 */
public record GameListParams(UUID playerId,
                             GameColour colour,
                             GameResult result,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @Size(max = 255) String event,
                             GameSort sort,
                             SortDirection direction,
                             @Min(0) @Max(100_000) Integer page,
                             @Min(1) @Max(100) Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 25;

    public GameListParams {
        sort = sort == null ? GameSort.PLAYED_ON : sort;
        direction = direction == null ? SortDirection.DESC : direction;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        event = searchTerm(event);
    }

    /**
     * Blank and absent mean the same thing. A blank term reaching the query would
     * become the pattern matching every game that has an event, and — because a
     * null column never satisfies {@code LIKE} — silently dropping every game that
     * has none. A filter the user believes they cleared would quietly remove rows.
     */
    private static String searchTerm(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** A colour on its own has nothing to narrow, and a filter that silently does nothing
     * is worse than one that is refused. */
    @AssertTrue(message = "colour requires playerId")
    public boolean isColourAccompaniedByAPlayer() {
        return colour == null || playerId != null;
    }

    /** An unsatisfiable range is a client defect worth naming, not an empty list. */
    @AssertTrue(message = "from must not be after to")
    public boolean isDateRangeOrdered() {
        return from == null || to == null || !from.isAfter(to);
    }

    /**
     * Called only after validation has passed, so the boxed values are non-null and
     * the cross-field rules already hold. {@link GameQuery} checks them again
     * anyway — it is the invariant's owner, and this is its input gate.
     */
    public GameQuery toQuery() {
        return new GameQuery(playerId, colour, result, from, to, event,
                sort, direction, page, size);
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `mvn -f services/core/pom.xml test -Dtest=GameListParamsTest`
Expected: PASS, 13 tests.

- [ ] **Step 5: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/api/GameListParams.java \
        services/core/src/test/java/com/chessapp/game/api/GameListParamsTest.java
git commit -m "Bind and validate the game list query parameters

One record, so the two cross-field rules — colour requires playerId, from
must not be after to — have a single home and fail as 400 problem+json the
same way the single-field ones do.

Defaults live here and nowhere else: GameQuery requires sort and direction
rather than defaulting them, so two places cannot disagree about what no
sort given means.

A blank event is treated as omitted. Left alone it would become the
pattern matching every game with an event and, because a null column never
satisfies LIKE, silently dropping every game without one.

Issue: #8"
```

---

### Task 6: The endpoint

**Files:**
- Create: `services/core/src/main/java/com/chessapp/game/api/GameSummaryResponse.java`
- Create: `services/core/src/main/java/com/chessapp/game/api/GamePageResponse.java`
- Modify: `services/core/src/main/java/com/chessapp/game/api/GameController.java`
- Test: `services/core/src/test/java/com/chessapp/game/api/GameApiIT.java`

**Interfaces:**
- Consumes: `GameListParams.toQuery()` (Task 5), `GameRepository.find(GameQuery)` (Task 4), `GameSummary`/`GamePage` (Task 2), and the existing nested `GameResponse.Side(UUID playerId, String name, Integer rating)`.
- Produces: `GET /api/games` returning `GamePageResponse(List<GameSummaryResponse> content, int page, int size, long totalElements, int totalPages)`.

- [ ] **Step 1: Write the failing test**

Append to `services/core/src/test/java/com/chessapp/game/api/GameApiIT.java`. Add these imports alongside the existing ones:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.hasSize;
```

Then add this block of tests to the class:

```java
    /**
     * The class shares one container with no cleanup between methods, so an
     * unfiltered list request sees every game every other test ever created. Every
     * list test therefore scopes itself with a filter only its own fixture matches:
     * a unique event string, or the player id the import reported.
     *
     * <p>That is why the defaults are asserted on a scoped request rather than on a
     * parameterless one, which the design describes. A parameterless request here
     * would see every game the import tests created and its assertions would depend
     * on execution order. The defaults themselves — page 0, size 25 — are still what
     * is being asserted, because none of them is supplied.
     */
    private static String pgnWithEvent(String white, String black, String event) {
        return """
                [Event "%s"]
                [Site "London ENG"]
                [Date "2026.03.14"]
                [Round "3.2"]
                [White "%s"]
                [Black "%s"]
                [Result "1-0"]
                [WhiteElo "1850"]
                [ECO "C60"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """.formatted(event, white, black);
    }

    private String importForListing(String event) throws Exception {
        return importing(pgnWithEvent("List White " + event, "List Black " + event, event))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void listsMatchingGamesWithTheDocumentedDefaults() throws Exception {
        String event = "Listing " + UUID.randomUUID();
        importForListing(event);

        mockMvc.perform(get("/api/games").param("event", event))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].event").value(event))
                .andExpect(jsonPath("$.content[0].white.name").value("List White " + event))
                .andExpect(jsonPath("$.content[0].white.rating").value(1850))
                .andExpect(jsonPath("$.content[0].black.rating").doesNotExist())
                .andExpect(jsonPath("$.content[0].playedOn").value("2026-03-14"))
                .andExpect(jsonPath("$.content[0].result").value("WHITE_WON"))
                .andExpect(jsonPath("$.content[0].eco").value("C60"))
                .andExpect(jsonPath("$.content[0].source").value("PGN_IMPORT"));
    }

    /**
     * A page of 25 rows would otherwise carry 25 complete move lists to render a
     * table that shows none of them.
     */
    @Test
    void doesNotCarryTheMovesOnAListRow() throws Exception {
        String event = "Moveless " + UUID.randomUUID();
        importForListing(event);

        mockMvc.perform(get("/api/games").param("event", event))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].movetext").doesNotExist())
                .andExpect(jsonPath("$.content[0].sourcePgn").doesNotExist());
    }

    @Test
    void filtersByPlayerAndColour() throws Exception {
        String event = "Coloured " + UUID.randomUUID();
        String body = importForListing(event);
        String whitePlayerId = objectMapper.readTree(body).get("white").get("playerId").asString();

        mockMvc.perform(get("/api/games")
                        .param("playerId", whitePlayerId)
                        .param("colour", "WHITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].event").value(event));

        mockMvc.perform(get("/api/games")
                        .param("playerId", whitePlayerId)
                        .param("colour", "BLACK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void answersAnEmptyPageWhenNothingMatches() throws Exception {
        mockMvc.perform(get("/api/games").param("event", "Nothing " + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    /**
     * The whole point of GameSort being an enum: an unknown sort field fails in
     * conversion, before a query exists, rather than being concatenated into one.
     */
    @Test
    void rejectsASortFieldOutsideTheWhitelist() throws Exception {
        mockMvc.perform(get("/api/games").param("sort", "movetext"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsAColourWithNoPlayerToNarrow() throws Exception {
        mockMvc.perform(get("/api/games").param("colour", "WHITE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsAnUnsatisfiableDateRange() throws Exception {
        mockMvc.perform(get("/api/games")
                        .param("from", "2026-06-01")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAPageSizeBeyondTheCap() throws Exception {
        mockMvc.perform(get("/api/games").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAPageOfNoRows() throws Exception {
        mockMvc.perform(get("/api/games").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsANegativePage() throws Exception {
        mockMvc.perform(get("/api/games").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAMalformedPlayerIdentifier() throws Exception {
        mockMvc.perform(get("/api/games").param("playerId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAResultOutsideTheEnum() throws Exception {
        mockMvc.perform(get("/api/games").param("result", "WHITE_LOST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnEventTermBeyondTheLengthCap() throws Exception {
        mockMvc.perform(get("/api/games").param("event", "x".repeat(256)))
                .andExpect(status().isBadRequest());
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -f services/core/pom.xml verify -Dit.test=GameApiIT`
Expected: the new tests fail — `GET /api/games` is not mapped, so every request answers 405.

- [ ] **Step 3: Write the two response records**

`services/core/src/main/java/com/chessapp/game/api/GameSummaryResponse.java`:

```java
package com.chessapp.game.api;

import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.GameSummary;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A game as a row of the list: {@link GameResponse}'s shape without the moves.
 *
 * <p>A page of 25 games would otherwise ship 25 complete move lists to render a
 * table that displays none of them. #9 keeps returning the full
 * {@code GameResponse} for one game, so the viewer still gets {@code movetext} when
 * it opens one.
 *
 * <p>Reuses {@link GameResponse.Side} rather than redeclaring an identical nested
 * record: it is the same concept, in the same package, and a client should see one
 * shape for a player on a game whichever endpoint it came from.
 *
 * <p>Optional metadata is present as null rather than omitted, matching
 * {@code GameResponse} — a client sees one shape whatever the document said.
 */
public record GameSummaryResponse(UUID id,
                                  GameResponse.Side white,
                                  GameResponse.Side black,
                                  String event,
                                  String site,
                                  String round,
                                  LocalDate playedOn,
                                  GameResult result,
                                  String eco,
                                  GameSource source) {

    public static GameSummaryResponse from(GameSummary summary) {
        return new GameSummaryResponse(summary.id(),
                side(summary.white()),
                side(summary.black()),
                summary.event(),
                summary.site(),
                summary.round(),
                summary.playedOn(),
                summary.result(),
                summary.eco(),
                summary.source());
    }

    private static GameResponse.Side side(GameSide side) {
        return new GameResponse.Side(side.playerId(), side.name(), side.rating());
    }
}
```

`services/core/src/main/java/com/chessapp/game/api/GamePageResponse.java`:

```java
package com.chessapp.game.api;

import com.chessapp.game.domain.GamePage;
import java.util.List;

/**
 * The list envelope.
 *
 * <p>Written out rather than serialising Spring Data's {@code PageImpl}, whose JSON
 * shape Spring Data itself warns is not stable across versions. Nothing here holds
 * one in any case — the query is hand-built Criteria — so this is a note for anyone
 * tempted to reintroduce it.
 *
 * <p>{@code totalPages} is derived here rather than in the domain {@link GamePage},
 * which stays the minimum a search produces. Total pages is presentation
 * arithmetic: it exists so a client can draw a pager.
 */
public record GamePageResponse(List<GameSummaryResponse> content,
                               int page,
                               int size,
                               long totalElements,
                               int totalPages) {

    public static GamePageResponse from(GamePage page) {
        return new GamePageResponse(
                page.content().stream().map(GameSummaryResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                totalPages(page.totalElements(), page.size()));
    }

    /**
     * Ceiling division in integer arithmetic. Floating point would round wrongly for
     * a large total, and an empty filtered set is zero pages rather than one — there
     * is no page to draw.
     */
    private static int totalPages(long totalElements, int size) {
        return (int) ((totalElements + size - 1) / size);
    }
}
```

- [ ] **Step 4: Add the endpoint**

In `services/core/src/main/java/com/chessapp/game/api/GameController.java`, add the imports:

```java
import com.chessapp.game.domain.GameRepository;
import org.springframework.web.bind.annotation.GetMapping;
```

Replace the field and constructor with:

```java
    private final ImportPgn importPgn;
    private final GameRepository games;

    public GameController(ImportPgn importPgn, GameRepository games) {
        this.importPgn = importPgn;
        this.games = games;
    }
```

And add the handler:

```java
    /**
     * Calls the repository directly rather than through an application-layer class.
     * Binding the parameters and mapping the page are both DTO conversion, which is
     * this layer's job, and there is nothing left to orchestrate — a use case here
     * would be a single delegating line. {@code GameRepository} is declared in the
     * domain, so this is the API layer depending on a domain port rather than on
     * persistence; what it does skip is the application layer, and whether read
     * paths should have one on principle is #41.
     *
     * <p>{@code params} is bound as a model attribute by constructor binding.
     * {@code @Valid} makes a failed binding or a violated constraint a
     * {@code MethodArgumentNotValidException}, which Spring renders as 400
     * problem+json because {@code spring.mvc.problemdetails.enabled} is on.
     */
    @GetMapping
    public GamePageResponse listGames(@Valid GameListParams params) {
        return GamePageResponse.from(games.find(params.toQuery()));
    }
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -f services/core/pom.xml verify -Dit.test=GameApiIT`
Expected: PASS — the pre-existing import tests plus 13 new list tests.

If a request that should be a 400 comes back as 500, the binding failure is not being converted: check that `@Valid` is present on the parameter and that `GameListParams` is being treated as a model attribute rather than as a single converted value.

- [ ] **Step 6: Run the whole suite**

Run: `mvn -f services/core/pom.xml verify`
Expected: BUILD SUCCESS, every unit test and every IT green.

- [ ] **Step 7: Commit**

```bash
git add services/core/src/main/java/com/chessapp/game/api/GameSummaryResponse.java \
        services/core/src/main/java/com/chessapp/game/api/GamePageResponse.java \
        services/core/src/main/java/com/chessapp/game/api/GameController.java \
        services/core/src/test/java/com/chessapp/game/api/GameApiIT.java
git commit -m "GET /games — list with filtering and pagination

Rows carry no movetext: a page of 25 games would otherwise ship 25
complete move lists to render a table that displays none of them. #9 keeps
the full representation for a single game.

The envelope is written out rather than a serialised PageImpl, whose JSON
shape Spring Data warns is unstable, and total pages is derived here
because it is presentation arithmetic.

The controller calls the domain repository directly. Binding and mapping
are DTO conversion and there is nothing to orchestrate; whether read paths
should have an application-layer use case on principle is #41.

Closes #8"
```

---

## Verification

After Task 6, confirm the endpoint end to end against a running application rather than only against MockMvc:

- [ ] Start PostgreSQL: `docker compose -f infra/docker-compose.yml up -d`
- [ ] Run the app: `mvn -f services/core/pom.xml spring-boot:run`
- [ ] Import a game, then list it:

```bash
curl -s -X POST localhost:8080/api/games -H 'Content-Type: application/json' \
  -d '{"pgn":"[Event \"Obs Club Championship\"]\n[White \"Green, Guy\"]\n[Black \"Club Opponent\"]\n[Result \"1-0\"]\n[Date \"2026.03.14\"]\n\n1. e4 e5 2. Nf3 Nc6 1-0\n"}'

curl -s 'localhost:8080/api/games?event=championship&size=5' | jq
curl -s 'localhost:8080/api/games?sort=movetext' | jq
```

Expected: the list returns the game with no `movetext` key and `totalPages: 1`; the bad sort returns 400 `application/problem+json`.
