# GameImport domain model and state machine

Date: 2026-10-01

Issue: [#12](https://github.com/guyAOgreen/Chess-App/issues/12) — M2, GameImport workflow

Chess terminology used here is defined in the [glossary](../../glossary.md).

## Goal

Introduce `GameImport` in `com.chessapp.gameimport`: an attempt to create a game
from a source that needs processing and review, such as a scoresheet photo. It is
deliberately separate from `Game`. A `GameImport` may be incomplete, ambiguous or
wrong. A `Game` is confirmed and canonical.

This issue fixes the lifecycle every later M2 issue builds on: upload (#14),
recognition (#15), validation (#16), review (#17) and confirmation (#18).

## Scope

In scope:

- `GameImportStatus`, the five states and the single table of allowed transitions;
- `GameImport`, an immutable aggregate whose transition methods enforce that table;
- `InvalidGameImportTransition`, thrown when a transition is not allowed;
- unit tests for every allowed and every rejected transition;
- replacing the "possible lifecycle" in `CONTEXT.md` with the agreed state machine.

Out of scope, each owned by another issue:

| Concern | Issue |
|---|---|
| `game_imports` table, migration, repository, `NewGameImport` | #14 |
| Scoresheet image references | #13, #14 |
| Recognition result data, `NotationRecognizer` | #15 |
| Validation issues on candidate moves | #16 |
| Creating a `Game` on confirmation | #18 |
| Owner / uploading user | #25 |
| Retrying a failed import | not planned |

## The state machine

```text
UPLOADED ──startProcessing──▶ PROCESSING ──markReadyForReview──▶ READY_FOR_REVIEW ──confirm──▶ CONFIRMED
                                  │
                                  └──fail(reason)──▶ FAILED
```

| From | Allowed to |
|---|---|
| `UPLOADED` | `PROCESSING` |
| `PROCESSING` | `READY_FOR_REVIEW`, `FAILED` |
| `READY_FOR_REVIEW` | `CONFIRMED` |
| `CONFIRMED` | none (terminal) |
| `FAILED` | none (terminal) |

Every pair not listed is rejected, including staying in the same state. A
self-transition such as confirming an already confirmed import is a duplicate
request, and should be visible rather than silently ignored.

## Decisions

### 1. Immutable record with transition methods

`GameImport` is a record. Each transition method returns a new instance and
leaves the original unchanged. This matches `Game` and `Player`, and rehydrating
from a database row later is just a constructor call.

The alternative was a sealed interface with one record per state, where illegal
transitions would not compile. It was rejected because an import loaded from a
repository arrives as the general type. Every caller would then `switch` before
acting, which turns the compile-time guarantee back into a runtime check and
costs five types instead of two.

### 2. One transition table, in the enum

`GameImportStatus.canTransitionTo(GameImportStatus)` is the only place the
allowed transitions are written down. `GameImport` asks it and does not repeat
the rules. Controllers and services never compare statuses to decide whether
an action is allowed. They call the transition and let it fail.

`GameImportStatus.isTerminal()` reports `CONFIRMED` and `FAILED`, so later
callers can ask the question without listing states.

### 3. FAILED is terminal

A failed import cannot be retried. The user starts a new import. Retry can be
added later as a single `FAILED → PROCESSING` entry if real failures turn out to
be transient. Adding it now would need attempt counting and idempotency rules
that nothing requires yet.

Only `PROCESSING` can fail. Before processing there is nothing to fail at. After
`READY_FOR_REVIEW` the user is reviewing, and a problem there is a review issue
(#16, #17), not a processing failure.

### 4. Illegal transitions throw `InvalidGameImportTransition`

Elsewhere, expected failures are sealed result types (`PgnParseResult`), because
bad PGN is ordinary user input. An illegal transition is not. It is a caller bug
or a concurrent duplicate request, such as confirming twice. So it is an
unchecked `InvalidGameImportTransition extends RuntimeException`, carrying `from`
and `to` and with a message naming both. #18 can map it to `409 Conflict`.

### 5. `failureReason` is present exactly when FAILED

`fail(String reason)` requires a non-blank reason, which is stored trimmed. The
compact constructor enforces the same invariant for every instance:

- status `FAILED` requires a non-blank `failureReason`;
- any other status requires `failureReason` to be null.

A failed import always says why. A non-failed import never carries a stale
reason.

### 6. `id` is required; there is no `NewGameImport` yet

As with `Game`, a `GameImport` is always persisted, so `id` is required. Ids are
assigned by the database (`uuidv7()`) once #14 adds the table. The not-yet-saved
request type (`NewGameImport`) is added by #14. Only #14 knows what it carries
(image references, uploader), and today it would be an empty type.

### 7. Confirmation does not create a Game

`confirm()` only moves the import to `CONFIRMED`. Turning the reviewed moves
into a canonical PGN and a `Game` is an application-layer use case in #18. The
domain model has no path from recognition output to a `Game`. That is the
structural guarantee behind "an AI transcription is never saved directly as a
Game".

## Types

All are in `com.chessapp.gameimport.domain`, with no Spring or JPA dependencies.

```java
public enum GameImportStatus {
    UPLOADED, PROCESSING, READY_FOR_REVIEW, CONFIRMED, FAILED;

    public boolean canTransitionTo(GameImportStatus next) { ... }
    public boolean isTerminal() { ... }
}

public record GameImport(UUID id, GameImportStatus status, String failureReason) {
    public GameImport { /* id and status required; failureReason invariant */ }

    public GameImport startProcessing()    { ... } // UPLOADED → PROCESSING
    public GameImport markReadyForReview() { ... } // PROCESSING → READY_FOR_REVIEW
    public GameImport fail(String reason)  { ... } // PROCESSING → FAILED
    public GameImport confirm()            { ... } // READY_FOR_REVIEW → CONFIRMED
}

public class InvalidGameImportTransition extends RuntimeException {
    public InvalidGameImportTransition(GameImportStatus from, GameImportStatus to) { ... }
    public GameImportStatus from() { ... }
    public GameImportStatus to() { ... }
}
```

Constructor validation follows the existing convention and throws
`IllegalArgumentException`. A bad transition throws
`InvalidGameImportTransition`.

`fail(reason)` checks the transition before the reason. Failing an import that
is not processing reports the illegal transition, whatever the reason was.

## Testing

Unit tests only (JUnit 5, AssertJ), in
`src/test/java/com/chessapp/gameimport/domain/`.

`GameImportStatusTest`:

- a parameterised test over all 25 (from, to) pairs, asserting `canTransitionTo`
  against the table above, so a rejected pair cannot go untested;
- `isTerminal` is true for exactly `CONFIRMED` and `FAILED`.

`GameImportTest`:

- each transition method moves to the expected status and keeps the id;
- transitions do not mutate the original instance;
- each method called from a disallowed state throws `InvalidGameImportTransition`
  with the right `from` and `to`;
- a missing `id` or `status` is rejected;
- `FAILED` without a reason, or with a blank one, is rejected;
- a non-`FAILED` status with a reason is rejected;
- `fail` trims its reason, and rejects a blank one;
- `fail` from a non-processing state throws the transition error, not a reason error.

## Documentation

In `CONTEXT.md`, the GameImport section's "possible lifecycle" and "the exact
state machine should be designed when implementing the workflow" are replaced
with the transition table above. The note that FAILED is terminal is added.
