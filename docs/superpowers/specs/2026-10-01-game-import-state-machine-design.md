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
- behavioural acceptance criteria for transitions and aggregate invariants;
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

Every pair not listed is rejected, including staying in the same state. The
domain does not silently accept duplicate transition commands. This does not
decide HTTP retry semantics: a later application contract may return the existing
confirmed Game for a repeated request without performing another transition.

An import starts in `UPLOADED` once its source has been accepted and durably
recorded. Rejected uploads do not create an import in this lifecycle.
`PROCESSING` means recognition and preparation of the review data are underway.
`READY_FOR_REVIEW` means that processing has produced a reviewable result, not
that all moves are legal, complete or unambiguous. Recognition uncertainty and
deterministic validation issues belong in review; they do not by themselves
make an import `FAILED`. A processing failure means the system could not produce
a reviewable result.

Corrections and validation during review leave the lifecycle status at
`READY_FOR_REVIEW`. Invalid confirmation input also leaves it there.
`CONFIRMED` means the reviewed result has successfully become a canonical Game.

## Decisions

### 1. Immutable record with transition methods

`GameImport` is a record. Each transition method returns a new instance and
leaves the original unchanged. This matches `Game` and `Player`. Rehydration
must enforce the same value invariants as ordinary construction; it restores a
snapshot rather than replaying lifecycle transitions. A constructor cannot prove
that an allowed transition history occurred.

The alternative was a sealed interface with one record per state, where illegal
transitions would not compile. It was rejected because an import loaded from a
repository arrives as the general type. Every caller would then `switch` before
acting, which turns the compile-time guarantee back into a runtime check and
costs five types instead of two.

### 2. One transition table, in the enum

`GameImportStatus.canTransitionTo(GameImportStatus)` is the only place the
allowed transitions are written down. `GameImport` asks it and does not repeat
the rules. Controllers and services do not duplicate this transition table. They
call the transition and let it fail. Application checks for permissions, review
validity and concurrent updates are separate concerns; an allowed lifecycle
transition alone does not authorise or validate an operation.

`GameImportStatus.isTerminal()` reports `CONFIRMED` and `FAILED`, so later
callers can ask the question without listing states.

### 3. FAILED is terminal

A failed import cannot be retried. The user starts a new import. Retry can be
designed later if real failures turn out to be transient. That would require
attempt identity, preservation of recognition history and rules for stale results
as well as a new transition. None of that is required for this lifecycle slice.

Only `PROCESSING` can transition to `FAILED`. Upload rejection is outside the
persisted lifecycle. After `READY_FOR_REVIEW` the user is reviewing, and a
problem there is a review issue (#16, #17), not a processing failure.

### 4. Illegal transitions throw `InvalidGameImportTransition`

Elsewhere, expected failures are sealed result types (`PgnParseResult`), because
bad PGN is ordinary user input that the caller must handle on the normal path.
An illegal transition is different. It comes from a caller bug or a stale or
duplicate request, such as confirming twice. That can happen, but it is never
the normal path, and the caller's only sensible response is to reject the
request. So it is an unchecked `InvalidGameImportTransition extends RuntimeException`, carrying `from`
and `to` and with a message naming both. #18 can map it to `409 Conflict`.

### 5. `failureReason` is present exactly when FAILED

`fail(String reason)` requires a non-blank reason, which is stored trimmed. The
compact constructor enforces the same invariant for every instance:

- status `FAILED` requires a non-blank `failureReason`;
- any other status requires `failureReason` to be null.

The constructor normalises a valid failure reason in the same way as `fail`.
Null, empty and whitespace-only reasons are rejected for `FAILED`. Even a blank
non-null reason is rejected for other statuses.

"Non-blank" means the reason contains at least one letter or digit. "Trimmed"
removes Unicode space separators, control characters and format characters
(`\p{Z}`, `\p{Cc}`, `\p{Cf}`) from both ends. `String.trim()` and `strip()` are
not enough. Both keep no-break spaces and zero-width characters, so a reason
made only of those would look blank and still be accepted.

A failed import always says why. A non-failed import never carries a stale
reason. The reason is an application-controlled, safe explanation, not a raw
provider response, stack trace or credential-bearing exception message. Detailed
infrastructure diagnostics remain outside the public domain failure reason.

### 6. `id` is required; there is no `NewGameImport` yet

As with `Game`, a `GameImport` represents an identified persisted import, so
`id` is required. A returned transition value is a proposed next snapshot; it
does not itself persist anything. Ids are assigned by the database (`uuidv7()`)
once #14 adds the table. The not-yet-saved request type (`NewGameImport`) is added by #14. Only #14 knows what it carries
(image references, uploader), and today it would be an empty type.

### 7. Confirmation does not create a Game

`confirm()` only moves the import to `CONFIRMED`. Turning the reviewed moves
into a canonical PGN and a `Game` is an application-layer use case in #18. The
domain model does not construct a `Game`. This separation alone does not enforce
human review or chess validity. The confirmation use case must require explicit
user confirmation of the current reviewed data and deterministic validation of
the complete submitted move sequence and required game metadata. Original
recognition data must remain separate from corrections and the confirmed result.

Persisting `CONFIRMED`, creating the Game and recording their association must
succeed atomically. If validation or Game creation fails, no confirmed status or
partial Game is committed. One import can create at most one Game. The association
and persistence mechanism belong to #18, but these are lifecycle requirements.

In this slice `GameImport` has no reference to a `Game`, so the model can
represent a `CONFIRMED` import without one. That is deliberate: there is no
`Game` to point at until #18 exists. The rule that `CONFIRMED` means "a Game
was created" is made to hold by #18. It confirms only inside the same
transaction that creates the Game, and it is expected to add the import → Game
link (for example `confirm(UUID gameId)`) at that point.

### 8. Transitions operate on a snapshot

The transition table rejects a command against the wrong status in memory. It
cannot prevent two callers loading the same status and both making an allowed
transition. Persistence and application use cases must reject stale writes and
ensure that only one competing transition commits. A late processing success
must not overwrite a committed failure, and concurrent confirmations must not
create multiple Games. The mechanism belongs to the persistence and workflow
specifications, not this domain-only slice.

## Types

All are in `com.chessapp.gameimport.domain`, with no Spring or JPA dependencies.

```java
public enum GameImportStatus {
    UPLOADED, PROCESSING, READY_FOR_REVIEW, CONFIRMED, FAILED;

    public boolean canTransitionTo(GameImportStatus next) { ... } // null → IllegalArgumentException
    public boolean isTerminal() { ... }
}

public record GameImport(UUID id, GameImportStatus status, String failureReason) {
    public GameImport { /* id and status required; failureReason invariant, trimmed */ }

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

Each transition method keeps the `id` and returns a new snapshot. A null target
status passed to `canTransitionTo` is invalid input and is rejected with
`IllegalArgumentException`. It is not treated as a lifecycle state.

Constructor validation follows the existing convention and throws
`IllegalArgumentException`. A bad transition throws
`InvalidGameImportTransition`, carrying the source and target statuses and a
message naming both.

`fail(reason)` checks the transition before the reason. Failing an import that
is not processing reports the illegal transition, whatever the reason was.

## Acceptance criteria

For this domain slice:

- Transition eligibility agrees with the table for all 25 source/target pairs.
- Exactly `CONFIRMED` and `FAILED` are terminal.
- Each allowed operation produces the expected status, preserves identity and
  leaves the original snapshot unchanged.
- Each operation from a disallowed state rejects the transition and identifies
  the correct source and target statuses.
- A missing identifier, missing status or null transition target is rejected.
- Construction and `fail` reject missing or blank failure reasons for
  `FAILED` and trim valid reasons consistently.
- Every other status rejects any non-null failure reason.
- `fail` from a non-processing state reports the transition error before
  checking the reason.

Later slices have their own lifecycle acceptance criteria, which follow from this
spec. They are recorded on the issues that own them:
[#14](https://github.com/guyAOgreen/Chess-App/issues/14) (stale writes),
[#16](https://github.com/guyAOgreen/Chess-App/issues/16) (ambiguous moves still
reach review) and [#18](https://github.com/guyAOgreen/Chess-App/issues/18)
(atomic, single-Game confirmation).

## Testing

Unit tests only (JUnit 5, AssertJ), in
`src/test/java/com/chessapp/gameimport/domain/`. Together they cover every
acceptance criterion above.

`GameImportStatusTest`:

- a parameterised test over all 25 (from, to) pairs, asserting `canTransitionTo`
  against the table, so a rejected pair cannot go untested;
- `canTransitionTo(null)` is rejected;
- `isTerminal` is true for exactly `CONFIRMED` and `FAILED`.

`GameImportTest`:

- each transition method moves to the expected status and keeps the id;
- transitions do not mutate the original instance;
- each method called from a disallowed state throws `InvalidGameImportTransition`
  with the right `from` and `to`;
- a missing `id` or `status` is rejected;
- `FAILED` with a null, empty or blank reason is rejected, by both the
  constructor and `fail`;
- a valid reason is trimmed, by both the constructor and `fail`;
- a non-`FAILED` status with any non-null reason, blank included, is rejected;
- `fail` from a non-processing state throws the transition error, not a reason error.

## Documentation

As part of this change, `CONTEXT.md` replaces its tentative GameImport lifecycle
with:

- the agreed transition table;
- the policy that `CONFIRMED` and `FAILED` are terminal;
- the meaning of `READY_FOR_REVIEW` and `CONFIRMED`.
