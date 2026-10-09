# GameImport State Machine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce the `GameImport` domain model and its explicit, tested lifecycle state machine in `com.chessapp.gameimport.domain`.

**Architecture:**
- `GameImportStatus` is an enum, and its `canTransitionTo` is the single transition table.
- `GameImport` is an immutable record. Its four transition methods ask that table and return a new snapshot, or throw `InvalidGameImportTransition`.
- This is a pure domain slice: no persistence, no Spring, no API.

**Tech Stack:** Java 25, JUnit 5 (including `junit-jupiter-params`, already on the classpath via `spring-boot-starter-test`), AssertJ.

**Spec:** [`docs/superpowers/specs/2026-10-01-game-import-state-machine-design.md`](../specs/2026-10-01-game-import-state-machine-design.md)

## Global Constraints

- **Java 25.** `JAVA_HOME` must point at a JDK 25 install (`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`).
- **Working directory for all Maven commands:** `services/core`.
- **No Spring or JPA in `domain/`.** No `org.springframework.*` and no `jakarta.persistence.*` imports.
- **Unit tests only.** Test classes are named `*Test` and run under surefire (`mvn test`). No `*IT`, no Testcontainers, no migration. Persistence belongs to #14.
- **Validation failures throw `IllegalArgumentException`**, which is the existing convention (see `GameValues`). An illegal transition throws `InvalidGameImportTransition`.
- **Match existing style:**
  - records with compact constructors;
  - Javadoc explaining *why*;
  - camelCase sentence test names, e.g. `rejectsAMissingIdBecauseAnImportIsAlwaysPersisted`.
- **Out of scope:** a `Game` reference on `GameImport`, `NewGameImport`, a repository, recognition data, retry.
- **Every task ends with a green `mvn test` and a commit.** Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

Each item below is pinned by a test in the task that owns the code:

1. **A duplicate command is not a no-op.** `confirm()` on a `CONFIRMED` import, or `startProcessing()` on a `PROCESSING` one, throws `InvalidGameImportTransition` rather than returning the same value. Covered by Task 3's "every disallowed source state" test, which includes self-transitions.
2. **`fail(null)` on a non-processing import reports the transition, not the reason.** Callers mapping errors to HTTP must see a 409-shaped error, not a 400-shaped one. Task 3.
3. **A rehydrated `FAILED` row with a blank reason is rejected** rather than becoming a failure with no explanation. Task 2.
4. **A non-`FAILED` row carrying `""` is rejected, not quietly normalised to null.** A stale or empty reason on a live import signals corrupt data. Task 2.
5. **Adding a new status later forces a decision about its transitions.** `canTransitionTo` is an exhaustive `switch` with no `default`, so a new constant fails to compile until the table is updated. `isTerminal` is derived from the table, so it cannot drift. This is pinned by the compiler and by Task 1's derived-terminal test.

---

## File Structure

All paths are relative to `services/core`.

| File | Responsibility |
| --- | --- |
| `src/main/java/com/chessapp/gameimport/domain/GameImportStatus.java` | The five states; the single transition table; terminal-state query |
| `src/main/java/com/chessapp/gameimport/domain/GameImport.java` | The aggregate snapshot: invariants and transition methods |
| `src/main/java/com/chessapp/gameimport/domain/InvalidGameImportTransition.java` | Unchecked; an illegal transition, carrying `from` and `to` |
| `src/test/java/com/chessapp/gameimport/domain/GameImportStatusTest.java` | All 25 pairs, terminal states, null target |
| `src/test/java/com/chessapp/gameimport/domain/GameImportTest.java` | Constructor invariants and transitions |
| `../../CONTEXT.md` (repo root) | Replace the tentative lifecycle with the agreed one |

---

### Task 1: `GameImportStatus` and the transition table

**Files:**
- Create: `src/main/java/com/chessapp/gameimport/domain/GameImportStatus.java`
- Test: `src/test/java/com/chessapp/gameimport/domain/GameImportStatusTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum GameImportStatus { UPLOADED, PROCESSING, READY_FOR_REVIEW, CONFIRMED, FAILED }`
  - `boolean canTransitionTo(GameImportStatus next)`, which throws `IllegalArgumentException` when `next` is null
  - `boolean isTerminal()`

- [ ] **Step 1: Write the failing test**

The expected table is written out independently in the test, not derived from the code under test.

```java
package com.chessapp.gameimport.domain;

import static com.chessapp.gameimport.domain.GameImportStatus.CONFIRMED;
import static com.chessapp.gameimport.domain.GameImportStatus.FAILED;
import static com.chessapp.gameimport.domain.GameImportStatus.PROCESSING;
import static com.chessapp.gameimport.domain.GameImportStatus.READY_FOR_REVIEW;
import static com.chessapp.gameimport.domain.GameImportStatus.UPLOADED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GameImportStatusTest {

    /** The spec's transition table, written out independently of the code under test. */
    private static final Set<List<GameImportStatus>> ALLOWED = Set.of(
            List.of(UPLOADED, PROCESSING),
            List.of(PROCESSING, READY_FOR_REVIEW),
            List.of(PROCESSING, FAILED),
            List.of(READY_FOR_REVIEW, CONFIRMED));

    static Stream<Arguments> everyPair() {
        return Arrays.stream(GameImportStatus.values())
                .flatMap(from -> Arrays.stream(GameImportStatus.values())
                        .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void allowsExactlyTheTransitionsInTheTable(GameImportStatus from, GameImportStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.contains(List.of(from, to)));
    }

    @Test
    void onlyConfirmedAndFailedAreTerminal() {
        assertThat(Arrays.stream(GameImportStatus.values()).filter(GameImportStatus::isTerminal))
                .containsExactlyInAnyOrder(CONFIRMED, FAILED);
    }

    @Test
    void rejectsANullTargetRatherThanTreatingItAsAState() {
        assertThatThrownBy(() -> UPLOADED.canTransitionTo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("next");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=GameImportStatusTest`
Expected: compilation failure, `cannot find symbol: class GameImportStatus`.

- [ ] **Step 3: Write the implementation**

```java
package com.chessapp.gameimport.domain;

import java.util.Arrays;

/**
 * Where a {@link GameImport} is in its lifecycle.
 *
 * <pre>
 * UPLOADED → PROCESSING → READY_FOR_REVIEW → CONFIRMED
 *                 └──────→ FAILED
 * </pre>
 *
 * <p>{@link #canTransitionTo} is the only place the allowed transitions are
 * written down. Callers attempt a transition and let it fail; they do not
 * compare statuses themselves.
 *
 * <p>{@code READY_FOR_REVIEW} means processing produced something reviewable,
 * not that the moves are legal, complete or unambiguous — those issues are
 * reviewed, they do not fail the import. {@code FAILED} means no reviewable
 * result could be produced, and is terminal: the user starts a new import.
 */
public enum GameImportStatus {
    UPLOADED,
    PROCESSING,
    READY_FOR_REVIEW,
    CONFIRMED,
    FAILED;

    /**
     * Whether an import in this status may move to {@code next}. Staying in the
     * same status is never allowed, so a duplicate command is visible rather
     * than silently ignored.
     *
     * <p>The switch has no {@code default} on purpose: a new status does not
     * compile until its transitions have been decided.
     */
    public boolean canTransitionTo(GameImportStatus next) {
        if (next == null) {
            throw new IllegalArgumentException("next status is required");
        }
        return switch (this) {
            case UPLOADED -> next == PROCESSING;
            case PROCESSING -> next == READY_FOR_REVIEW || next == FAILED;
            case READY_FOR_REVIEW -> next == CONFIRMED;
            case CONFIRMED, FAILED -> false;
        };
    }

    /** No transition leaves this status. Derived from the table so the two cannot drift. */
    public boolean isTerminal() {
        return Arrays.stream(values()).noneMatch(this::canTransitionTo);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=GameImportStatusTest`
Expected: PASS, 27 tests (25 pairs + 2).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chessapp/gameimport/domain/GameImportStatus.java src/test/java/com/chessapp/gameimport/domain/GameImportStatusTest.java
git commit -m "Add GameImportStatus with its transition table (#12)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `GameImport` record and its invariants

**Files:**
- Create: `src/main/java/com/chessapp/gameimport/domain/GameImport.java`
- Test: `src/test/java/com/chessapp/gameimport/domain/GameImportTest.java`

**Interfaces:**
- Consumes: `GameImportStatus` (Task 1).
- Produces: `record GameImport(UUID id, GameImportStatus status, String failureReason)`, whose constructor enforces:
  - `id` and `status` are required;
  - `FAILED` requires a non-blank reason, stored trimmed;
  - every other status requires a null reason.

- [ ] **Step 1: Write the failing test**

```java
package com.chessapp.gameimport.domain;

import static com.chessapp.gameimport.domain.GameImportStatus.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GameImportTest {

    private static final UUID ID = UUID.fromString("0199a1b2-0000-7000-8000-000000000001");

    @ParameterizedTest
    @EnumSource(value = GameImportStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    void acceptsAnImportInANonFailedStatusWithNoReason(GameImportStatus status) {
        GameImport gameImport = new GameImport(ID, status, null);

        assertThat(gameImport.id()).isEqualTo(ID);
        assertThat(gameImport.status()).isEqualTo(status);
        assertThat(gameImport.failureReason()).isNull();
    }

    @Test
    void rejectsAMissingIdBecauseAnImportIsAlwaysPersisted() {
        assertThatThrownBy(() -> new GameImport(null, GameImportStatus.UPLOADED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejectsAMissingStatus() {
        assertThatThrownBy(() -> new GameImport(ID, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void trimsTheReasonOfAFailedImport() {
        GameImport gameImport = new GameImport(ID, FAILED, "  scoresheet unreadable \n");

        assertThat(gameImport.failureReason()).isEqualTo("scoresheet unreadable");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void rejectsAFailedImportWithoutAReasonSoAFailureAlwaysSaysWhy(String reason) {
        assertThatThrownBy(() -> new GameImport(ID, FAILED, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }

    @ParameterizedTest
    @EnumSource(value = GameImportStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsAReasonOnAnImportThatHasNotFailed(GameImportStatus status) {
        assertThatThrownBy(() -> new GameImport(ID, status, "scoresheet unreadable"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsEvenABlankReasonOnAnImportThatHasNotFailed(String reason) {
        assertThatThrownBy(() -> new GameImport(ID, GameImportStatus.READY_FOR_REVIEW, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=GameImportTest`
Expected: compilation failure, `cannot find symbol: class GameImport`.

- [ ] **Step 3: Write the implementation**

```java
package com.chessapp.gameimport.domain;

import java.util.UUID;

/**
 * An attempt to create a game from a source that needs processing and review,
 * such as a scoresheet photo. Deliberately separate from {@code Game}: an import
 * may be incomplete, ambiguous or wrong, a {@code Game} is confirmed.
 *
 * <p>A {@code GameImport} always exists in the database, so {@code id} is never
 * null. Construction restores a snapshot rather than replaying a history, so the
 * same invariants apply whether the value is new or rehydrated from a row.
 *
 * <p>{@code failureReason} is present exactly when the status is
 * {@link GameImportStatus#FAILED}. It is an application-controlled explanation,
 * never a raw provider response or exception message.
 */
public record GameImport(UUID id, GameImportStatus status, String failureReason) {

    public GameImport {
        if (id == null) {
            throw new IllegalArgumentException("id is required; a GameImport is always persisted");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        failureReason = failureReason(status, failureReason);
    }

    /**
     * A non-failed import with any reason, even a blank one, is rejected rather
     * than normalised: it signals a stale or corrupt value, not an absent one.
     */
    private static String failureReason(GameImportStatus status, String raw) {
        if (status != GameImportStatus.FAILED) {
            if (raw != null) {
                throw new IllegalArgumentException(
                        "failureReason must be null unless status is FAILED, status was: " + status);
            }
            return null;
        }
        if (raw == null) {
            throw new IllegalArgumentException("failureReason is required when status is FAILED");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("failureReason must not be blank");
        }
        return trimmed;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest='GameImport*Test'`
Expected: PASS, both test classes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chessapp/gameimport/domain/GameImport.java src/test/java/com/chessapp/gameimport/domain/GameImportTest.java
git commit -m "Add GameImport with its status and failure-reason invariants (#12)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Transitions, `InvalidGameImportTransition`, and CONTEXT.md

**Files:**
- Create: `src/main/java/com/chessapp/gameimport/domain/InvalidGameImportTransition.java`
- Modify: `src/main/java/com/chessapp/gameimport/domain/GameImport.java` (add transition methods)
- Modify: `src/test/java/com/chessapp/gameimport/domain/GameImportTest.java` (add transition tests)
- Modify: `CONTEXT.md` at the repo root (GameImport section, currently around lines 266–287)

**Interfaces:**
- Consumes: `GameImportStatus.canTransitionTo` (Task 1), `GameImport`'s constructor (Task 2).
- Produces:
  - on `GameImport`: `GameImport startProcessing()`, `GameImport markReadyForReview()`, `GameImport fail(String reason)`, `GameImport confirm()`;
  - `class InvalidGameImportTransition extends RuntimeException`, with constructor `(GameImportStatus from, GameImportStatus to)` and accessors `from()` and `to()`.

- [ ] **Step 1: Write the failing tests**

Add these imports to `GameImportTest`:

```java
import static com.chessapp.gameimport.domain.GameImportStatus.CONFIRMED;
import static com.chessapp.gameimport.domain.GameImportStatus.PROCESSING;
import static com.chessapp.gameimport.domain.GameImportStatus.READY_FOR_REVIEW;
import static com.chessapp.gameimport.domain.GameImportStatus.UPLOADED;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Arrays;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
```

Add these members to the class body:

```java
    /** A valid import in {@code status}; a failed one needs a reason to exist. */
    private static GameImport inStatus(GameImportStatus status) {
        return new GameImport(ID, status, status == FAILED ? "scoresheet unreadable" : null);
    }

    @Test
    void startProcessingMovesAnUploadedImportToProcessing() {
        GameImport processing = inStatus(UPLOADED).startProcessing();

        assertThat(processing).isEqualTo(new GameImport(ID, PROCESSING, null));
    }

    @Test
    void markReadyForReviewMovesAProcessingImportToReadyForReview() {
        GameImport ready = inStatus(PROCESSING).markReadyForReview();

        assertThat(ready).isEqualTo(new GameImport(ID, READY_FOR_REVIEW, null));
    }

    @Test
    void failMovesAProcessingImportToFailedWithATrimmedReason() {
        GameImport failed = inStatus(PROCESSING).fail("  recognition timed out ");

        assertThat(failed).isEqualTo(new GameImport(ID, FAILED, "recognition timed out"));
    }

    @Test
    void confirmMovesAnImportReadyForReviewToConfirmed() {
        GameImport confirmed = inStatus(READY_FOR_REVIEW).confirm();

        assertThat(confirmed).isEqualTo(new GameImport(ID, CONFIRMED, null));
    }

    @Test
    void aTransitionReturnsANewSnapshotAndLeavesTheOriginalUnchanged() {
        GameImport uploaded = inStatus(UPLOADED);

        GameImport processing = uploaded.startProcessing();

        assertThat(processing).isNotSameAs(uploaded);
        assertThat(uploaded.status()).isEqualTo(UPLOADED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void failRequiresAReasonSoAFailureAlwaysSaysWhy(String reason) {
        assertThatThrownBy(() -> inStatus(PROCESSING).fail(reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }

    @ParameterizedTest
    @EnumSource(value = GameImportStatus.class, names = "PROCESSING", mode = EnumSource.Mode.EXCLUDE)
    void failFromAnyOtherStatusReportsTheTransitionBeforeCheckingTheReason(GameImportStatus from) {
        assertThatThrownBy(() -> inStatus(from).fail(null))
                .isInstanceOf(InvalidGameImportTransition.class);
    }

    private record Operation(String name, GameImportStatus allowedFrom, GameImportStatus to,
                             UnaryOperator<GameImport> apply) {
    }

    private static final Operation[] OPERATIONS = {
            new Operation("startProcessing", UPLOADED, PROCESSING, GameImport::startProcessing),
            new Operation("markReadyForReview", PROCESSING, READY_FOR_REVIEW, GameImport::markReadyForReview),
            new Operation("fail", PROCESSING, FAILED, gameImport -> gameImport.fail("recognition timed out")),
            new Operation("confirm", READY_FOR_REVIEW, CONFIRMED, GameImport::confirm),
    };

    /** Every operation paired with every status it must not be called from, self-transitions included. */
    static Stream<Arguments> everyDisallowedSourceState() {
        return Arrays.stream(OPERATIONS)
                .flatMap(operation -> Arrays.stream(GameImportStatus.values())
                        .filter(from -> from != operation.allowedFrom())
                        .map(from -> Arguments.of(operation.name(), from, operation)));
    }

    @ParameterizedTest(name = "{0} from {1}")
    @MethodSource("everyDisallowedSourceState")
    void rejectsAnOperationFromAStatusItIsNotAllowedFrom(String name, GameImportStatus from, Operation operation) {
        InvalidGameImportTransition error = catchThrowableOfType(
                InvalidGameImportTransition.class, () -> operation.apply().apply(inStatus(from)));

        assertThat(error).isNotNull();
        assertThat(error.from()).isEqualTo(from);
        assertThat(error.to()).isEqualTo(operation.to());
        assertThat(error).hasMessageContaining(from.name()).hasMessageContaining(operation.to().name());
    }
```

Note: in AssertJ 3.26+ the signature is `catchThrowableOfType(Class<T>, ThrowingCallable)`. If the build reports the argument order the other way round (older AssertJ), swap the two arguments.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=GameImportTest`
Expected: compilation failure, `cannot find symbol` for `startProcessing` and `InvalidGameImportTransition`.

- [ ] **Step 3: Write the exception**

```java
package com.chessapp.gameimport.domain;

/**
 * A {@link GameImport} was asked to move to a status its current status does not
 * allow — for example confirming an import twice, or a late processing result
 * arriving after the import has failed.
 *
 * <p>Unchecked, unlike the sealed results used for bad PGN: this is not ordinary
 * user input with a normal recovery path. It is a caller bug or a stale or
 * duplicate request, and the only sensible response is to reject it — at the HTTP
 * boundary, {@code 409 Conflict}.
 */
public class InvalidGameImportTransition extends RuntimeException {

    private final GameImportStatus from;
    private final GameImportStatus to;

    public InvalidGameImportTransition(GameImportStatus from, GameImportStatus to) {
        super("GameImport cannot move from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public GameImportStatus from() {
        return from;
    }

    public GameImportStatus to() {
        return to;
    }
}
```

- [ ] **Step 4: Add the transition methods to `GameImport`**

Insert these after the compact constructor in `GameImport.java`, before the private `failureReason` helper:

```java
    /** {@code UPLOADED → PROCESSING}. */
    public GameImport startProcessing() {
        return transitionTo(GameImportStatus.PROCESSING, null);
    }

    /** {@code PROCESSING → READY_FOR_REVIEW}: a reviewable result exists, not necessarily a legal one. */
    public GameImport markReadyForReview() {
        return transitionTo(GameImportStatus.READY_FOR_REVIEW, null);
    }

    /** {@code PROCESSING → FAILED}: no reviewable result could be produced. */
    public GameImport fail(String reason) {
        return transitionTo(GameImportStatus.FAILED, reason);
    }

    /**
     * {@code READY_FOR_REVIEW → CONFIRMED}. This only changes the import's
     * status; creating the {@code Game} from the reviewed moves is the
     * confirmation use case's job (#18), in the same transaction.
     */
    public GameImport confirm() {
        return transitionTo(GameImportStatus.CONFIRMED, null);
    }

    /**
     * Checks the transition before building the next snapshot, so an illegal
     * transition is reported even when the accompanying reason is also invalid.
     * The returned value is a proposed next snapshot; it persists nothing.
     */
    private GameImport transitionTo(GameImportStatus next, String reason) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidGameImportTransition(status, next);
        }
        return new GameImport(id, next, reason);
    }
```

- [ ] **Step 5: Run all the tests to verify they pass**

Run: `mvn test`
Expected: `BUILD SUCCESS`, including `GameImportStatusTest` and `GameImportTest`. The new parameterised test reports 16 cases (4 operations × 4 disallowed states).

- [ ] **Step 6: Update CONTEXT.md**

In the repo-root `CONTEXT.md` GameImport section, replace everything from the line `Possible lifecycle:` up to and including `The exact state machine should be designed when implementing the workflow.` with:

````markdown
Lifecycle:

```text
UPLOADED → PROCESSING → READY_FOR_REVIEW → CONFIRMED
               └──────→ FAILED
```

| From | Allowed to |
|---|---|
| `UPLOADED` | `PROCESSING` |
| `PROCESSING` | `READY_FOR_REVIEW`, `FAILED` |
| `READY_FOR_REVIEW` | `CONFIRMED` |
| `CONFIRMED` | none (terminal) |
| `FAILED` | none (terminal) |

Every other transition is rejected, including repeating the current state.

* `UPLOADED`: the source has been accepted and durably recorded. Rejected
  uploads never create an import.
* `READY_FOR_REVIEW`: processing produced a reviewable result. It does not mean
  the moves are legal, complete or unambiguous. Those issues are reviewed and
  corrected here, and do not fail the import.
* `CONFIRMED`: the reviewed result has become a canonical Game. Confirmation and
  Game creation commit together.
* `FAILED`: no reviewable result could be produced. It is terminal, so the user
  starts a new import.

Design: `docs/superpowers/specs/2026-10-01-game-import-state-machine-design.md`.
````

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/chessapp/gameimport/domain/ src/test/java/com/chessapp/gameimport/domain/GameImportTest.java ../../CONTEXT.md
git commit -m "Add GameImport transitions and document the lifecycle (#12)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
