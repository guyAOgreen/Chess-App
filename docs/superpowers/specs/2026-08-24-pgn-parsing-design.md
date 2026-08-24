# PGN parsing and validation

Date: 2026-08-24

Issue: [#6](https://github.com/guyAOgreen/Chess-App/issues/6) — M1, Game database

Chess terminology used here is defined in the [glossary](../../glossary.md).

## Goal

Turn arbitrary PGN text into either a validated game or a clear reason it was
rejected, deterministically and without ever repairing what it was given.

Two units, pointing in opposite directions:

* a **parser** that reads a PGN document and produces validated chess facts;
* an **assembler** that writes the canonical PGN document for a stored `Game`.

PGN import (#7) is the first consumer of both. Scoresheet recognition (#16) is
the second consumer of the parser, and reaches it through the same contract.

## Decisions

### 1. chesslib is sealed inside a `chess` module

[ADR 0001](../../adr/0001-java-chess-rules-library.md) requires that no code
outside the chess module imports `com.github.bhlangonijr.*`. Exactly one class,
`ChesslibPgnParser`, does so. Everything else speaks our own types.

The wrapper is not ceremony. ADR 0001 records that `doMove` and `isMoveLegal` do
not enforce chess legality, that failures arrive as unchecked
`ArrayIndexOutOfBoundsException` as well as the declared checked exception, and
that `PgnIterator` validates lazily. Those constraints have to be enforced in one
place rather than at every call site, and a wrapper is what makes the identified
fallback library a real option rather than a sentence in an ADR.

`PgnParser` is an interface with a single implementation for the same reason.

### 2. The assembler lives in the game module, not the chess module

Assembly needs a `Game`; parsing must not know that `Game` exists. Putting both
in one PGN module would give the chesslib wrapper a dependency on our aggregate,
which is the coupling ADR 0001 exists to prevent.

The split costs nothing, because the two share no machinery.
[ADR 0002](../../adr/0002-game-storage.md) already established that assembly is
pure string work over metadata plus `movetext`: it needs no chess rules at all.

### 3. Invalid PGN is a return value, not an exception

Parsing returns a sealed `PgnParseResult`: either `Parsed` or `Rejected`. For an
import endpoint fed by users, an invalid document is an expected outcome rather
than an exceptional condition, and a sealed type makes the failure impossible to
forget at the call site.

`Rejected` carries one `PgnError`, not a list. chesslib stops at the first move it
cannot play, so a list would be honest about document-level problems and
misleading about moves — it would imply we had found every bad move when we had
found the first.

### 4. The parser owns PGN document semantics

Interpreting `2026.??.??`, `?` and `WhiteElo "unrated"` is knowledge about PGN
documents, not about our `Game`. It belongs with the reader, so #7 receives values
that are already in our types and does no re-interpretation.

### 5. `GameResult` is shared rather than duplicated

`ParsedGame` returns the existing `com.chessapp.game.domain.GameResult`. It
already carries `pgnToken()`, so it is PGN vocabulary that happens to live in the
game domain, and that package is pure — no Spring, no chesslib, nothing that makes
depending on it costly.

The alternative considered was moving `GameResult` into the `chess` module, which
would point the dependency arrow the more natural way. Rejected as churn on merged
code for a gain that is presentational; if a second chess-vocabulary type appears
later, the move becomes worth making and is mechanical.

### 6. One game per document

M1 accepts one game per import, so a multi-game document is rejected rather than
silently taking the first game. ADR 0002 requires a future batch workflow to define
its own document-level provenance, so quietly accepting a multi-game file now would
store a `source_pgn` whose meaning does not match its column.

### 7. An unknown player is rejected at parse time

`[White "?"]` is legal PGN, and ADR 0002 together with the
[player domain design](2026-08-22-player-domain-design.md) settled that such a game
cannot be stored: there is no placeholder `Player`, and `?` cannot be a
`display_name`.

Resolution of names to players stays in #7. The check here is only that the
document names both players, which turns an eventual foreign-key or check-constraint
failure into a clear message about the file the user submitted.

### 8. M1 accepts only the standard initial position

PGN can describe a game that starts from another position with the `SetUp` and
`FEN` tags. The current `Game` model stores neither the initial position nor those
tags, and canonical assembly deliberately emits only modelled metadata. Accepting
such a document would therefore create `movetext` that is valid only from an
unstored position and cannot be replayed from the assembled PGN.

The parser rejects any document containing either `SetUp` or `FEN`, including a
redundant standard-position FEN. This deliberately narrow rule avoids defining
the inconsistent combinations (`SetUp` without `FEN`, `FEN` without `SetUp`, or
`SetUp "0"` with `FEN`) when M1 has no use case for any of them. Supporting composed
positions later requires modelling the initial position on `Game`, updating the
assembler, and adding replay and persistence tests as one coherent change.

## Package layout

```text
com.chessapp.chess
├── PgnParser.java            our contract
├── PgnParseResult.java       sealed: Parsed | Rejected
├── ParsedGame.java
├── PgnError.java
├── PgnErrorCode.java
└── chesslib/
    └── ChesslibPgnParser.java   the only file importing com.github.bhlangonijr.*

com.chessapp.game.domain
└── CanonicalPgn.java
```

The `chess` module has no `api`, `application` or `persistence` package. It serves
no HTTP, holds no state and touches no database; it is a rules wrapper, and the
feature-layer convention does not apply to it.

`ChesslibPgnParser` is a `@Component` so #7 can inject `PgnParser`. The interface
and the value types stay free of Spring.

## The parser contract

```java
public interface PgnParser {
    PgnParseResult parse(String pgn);
}

public sealed interface PgnParseResult {
    record Parsed(ParsedGame game) implements PgnParseResult {}
    record Rejected(PgnError error) implements PgnParseResult {}
}

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
                         String movetext) {}

public record PgnError(PgnErrorCode code, String message, Integer ply) {}
```

All record values returned by a successful parse are immutable values owned by
the application; no chesslib object or mutable collection crosses the boundary.
`parse(null)`, blank input and text containing no readable game return
`Rejected(NOT_PGN, ...)` rather than throwing.

The wrapper catches `RuntimeException` and translates it, per ADR 0001's constraint
that chesslib signals some failures with an unchecked
`ArrayIndexOutOfBoundsException` rather than the declared checked exception. It does
not catch `Error`. That is the whole rule: a defect in our own code and a defect in
chesslib arrive through the same frames with the same types, so there is no runtime
test that separates them, and no attempt should be made to write one.

`ply` is a 1-based half-move index — ply 1 is White's first move — and is null for
errors that are not about a specific move. `message` is written for a person
looking at their own file, and names the move number and SAN where it has them.

`movetext` satisfies the rules `Game.movetext` enforces: SAN with move numbers, no
tag pairs, no terminal result token. It comes from
`MoveList.toSanWithMoveNumbers()`, which ADR 0001 verified produces clean movetext,
with any result token stripped.

Unmodelled tags — `TimeControl`, `Termination`, `Annotator` and provider-specific
ones — are not returned. ADR 0002 keeps them recoverable from `source_pgn`, which
#7 stores.

## Validation rules

| Code | Rejected when | `ply` |
| --- | --- | --- |
| `NOT_PGN` | No game can be read from the text | null |
| `MULTIPLE_GAMES` | The document holds more than one game | null |
| `NON_STANDARD_START_POSITION` | `SetUp` or `FEN` is present | null |
| `NO_MOVES` | The game has no moves | null |
| `UNREADABLE_MOVE` | SAN at this ply cannot be understood | set |
| `ILLEGAL_MOVE` | SAN at this ply is not legal in the reconstructed position | set |
| `PLAYER_UNKNOWN` | `White` or `Black` is absent, blank or `?` | null |
| `RESULT_MISSING` | Neither a `Result` tag nor a terminal token is present | null |
| `RESULT_CONFLICT` | The `Result` tag and the terminal token disagree | null |
| `RESULT_CONTRADICTS_POSITION` | The declared result disagrees with the final position | null |

Only the first problem found is reported, and the order is fixed so the same
document always produces the same error: document structure (`NOT_PGN`,
`MULTIPLE_GAMES`, `NON_STANDARD_START_POSITION`), then moves (`NO_MOVES`,
`UNREADABLE_MOVE`, `ILLEGAL_MOVE`), then tags (`PLAYER_UNKNOWN`), then the result.
Moves come before player and result tags because a file
whose moves do not reconstruct is broken in a way the user must fix first, and
because the result checks need the reconstructed final position.

### Resolving the result

Per ADR 0002, and in this order:

1. Tag and terminal token both present: they must agree, or `RESULT_CONFLICT`.
2. Exactly one present: it supplies the result.
3. Neither present: `RESULT_MISSING`.

Then the reconstructed final position must not contradict it. Checkmate requires
the mating side to have won; stalemate requires a draw. A decisive or drawn result
in a non-terminal position stays valid, because resignation, time forfeiture and
draw agreement are not derivable from the board.

### Values that become null rather than errors

* **Date.** `playedOn` is set only when the date is fully known and real. Any `?`
  component, and impossible dates such as `2026.02.30`, yield null. This matches
  ADR 0002's `played_on` column.
* **Ratings.** Absent, `?`, blank or non-numeric `WhiteElo` and `BlackElo` yield
  null.
* **Optional tags.** `Event`, `Site`, `Round` and `ECO` yield null when absent,
  blank or `?`, matching the normalisation `NewGame` already performs.

## Canonical PGN assembly

`CanonicalPgn.from(Game)` returns the document, assembled per ADR 0002:

1. Seven Tag Roster in specification order — `Event`, `Site`, `Date`, `Round`,
   `White`, `Black`, `Result`. Unknown strings are `?`; an unknown date is
   `????.??.??`.
2. `WhiteElo`, `BlackElo` and `ECO` when known.
3. A blank line, `movetext`, then the result token from the `result` column.

Tag values are escaped according to PGN string rules: `\` becomes `\\` and `"`
becomes `\"`. Assembly uses `\n` line endings and ends with one trailing newline,
so identical `Game` values produce byte-for-byte identical text on every platform.

Escaping alone is not enough, for a reason worth stating precisely. Escaping does
prevent tag-pair injection: an escaped `"` cannot close the string, so no value can
break out and forge a tag. The problem with a line break is validity rather than
injection — the PGN specification defines a string token as printing characters
between quotation marks, so an embedded newline makes the document invalid whatever
the escaping, and line-oriented readers mis-parse it.

So the values that become tags reject control characters outright: `GameSide.name`,
which supplies the `White` and `Black` tags, and `Event`, `Site`, `Round` and `ECO`.
That is the whole of the `Game` aggregate's contribution to the tag pair section.

`Player.displayName` is deliberately **not** included. A player's stored name never
reaches a PGN document — the tags come from the game-time snapshot on `Game`, which
is the point of storing snapshots at all. There may be good reasons to reject
control characters in display names, from search behaviour to the unique index, but
they are not PGN reasons and do not belong to this change.

Game-time name snapshots supply `White` and `Black`, so a later rename or player
merge does not rewrite historical exports.

No chesslib. ADR 0001 records that `Game.toPgn()` injects tags that were not in the
input, reorders the tag pair section and mangles comment spacing, which is why
assembly is ours.

## Build changes

`services/core/pom.xml` gains the JitPack repository and
`com.github.bhlangonijr:chesslib:1.3.7`, pinned exactly — never a branch reference
or `-SNAPSHOT`, per ADR 0001.

## Changes to the game module

Canonical assembly is where the control-character rule becomes load-bearing, so it
is added here rather than left for the first malformed export to find.

`GameValues` rejects control characters in `GameSide.name` and in the optional tags
`Event`, `Site`, `Round` and `ECO`. `GameTest`, `NewGameTest` and `GameSideTest`
gain cases for it, on both newly created and rehydrated values.

**A Flyway migration `V3` adds the matching `CHECK` constraints**, because #5
established that every domain rule is mirrored in the database, and a domain rule
without one leaves the two disagreeing — the divergence found in review on #35, and
the reason `movetext` validation had to be rewritten. `GameSchemaIT` gains a case
per constraint.

The constraints cannot reuse the `btrim` idiom from `V2`. PostgreSQL's single
argument `btrim` strips spaces only, while Java's `trim()` strips every character up
to and including the space, so the two are not equivalent for tabs and newlines. The
rule needs an explicit pattern, `!~ '[[:cntrl:]]'`.

## Testing

Parsing and assembly are unit tests throughout: neither touches HTTP or the
database, so no Spring context and no Testcontainers. The one exception is
`GameSchemaIT`, which already exists and gains cases for the `V3` constraints.

**`ChesslibPgnParserTest`** — a real PGN parses to the expected tags, movetext and
result; movetext normalisation, including `O-O`, `=Q` promotion and check suffixes;
one case per row of the validation table; date, rating and optional-tag
normalisation; null and blank input; an annotated game parsing with its comments,
NAGs and variations dropped from `movetext`.

**`ChesslibContractTest`** — one test per constraint in ADR 0001, expressed through
our API rather than the library's, so a library upgrade that regresses one fails our
build instead of corrupting a game:

1. A pawn moving three squares is rejected, rather than accepted as `doMove` would.
2. SAN capturing the king surfaces as a `Rejected`, not as
   `ArrayIndexOutOfBoundsException`.
3. A document whose movetext is invalid is `Rejected` — the lazy-validation trap,
   where a `Game` is returned before its moves have been read.
4. A terminal result token in the document does not leak into `movetext`.
5. Our assembled PGN carries the Seven Tag Roster in order and none of the tags
   `Game.toPgn()` injects. Asserted in `CanonicalPgnTest`, where the assembler is,
   and listed here so every constraint has a traceable test.
6. Concurrent parses do not interfere, pinning the one-`Board`-per-operation rule.

**`CanonicalPgnTest`** — tag order, unknown-value conventions, optional tags present
and absent, escaping quotes and backslashes, deterministic line endings and the
result token for each `GameResult`.

**`PgnRoundTripTest`** — parse, build a `Game`, assemble, parse again: the same
movetext and result both times. This is the property that makes "a game has exactly
one canonical PGN" true in practice rather than by assertion.

## Risks

* **JitPack is a build-time dependency.** ADR 0001 accepted this; caching the Maven
  repository is the CI pipeline's job (#26), and mirroring the 145 KB jar internally
  is the fallback.
* **First real exercise of chesslib on JDK 25.** The evaluation probe ran on JDK 21,
  the only locally installed JDK. The bytecode is Java 11 so it should load, but #28
  owns verifying the whole build on 25, and this is where a problem would first show.
* **chesslib's PGN reader is tolerant by design.** It accepts documents we might
  prefer to reject. The contract tests pin the behaviour we depend on; tolerance we
  have not pinned may change under an upgrade without failing our build.

## Known limitations

* A non-numeric rating is dropped rather than rejected, so `WhiteElo "unrated"`
  imports as an unrated game. Rejecting an otherwise valid game over a rating tag
  would be worse.
* Partial dates lose precision, as ADR 0002 recorded. The original tag survives in
  `source_pgn`.
* Comments, NAGs and variations do not reach canonical PGN, so an annotated import
  replays without its annotations until they are modelled.
* Games beginning from a `FEN` position are rejected until `Game` can retain and
  emit their initial position.
* Emitted movetext is not wrapped at 80 columns. Readers accept long lines, and
  wrapping is cosmetic until an export feature asks for it.
* A move that is legal but absurd is accepted, because it is legal. Deciding that a
  recognised game is implausible is #16's problem, not this one's.

## Out of scope

* **The import endpoint** — #7 owns orchestration, player resolution and
  persistence.
* **ECO classification.** The tag is read, never derived. ADR 0001 left opening
  classification undecided.
* **FEN and Zobrist exposure.** ADR 0001 places them in this module, but M1 has no
  consumer: ADR 0002 persists no derived data, and the game viewer re-parses
  movetext. #22 chooses an indexing strategy against real queries.
* **Recognition candidate elimination** — #16, which consumes this parser.
* **Multi-game uploads**, which need their own provenance model.
