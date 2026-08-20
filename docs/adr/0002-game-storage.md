# 2. Canonical PGN and derived move data storage

Date: 2026-08-19

Status: Accepted

Issue: [#3](https://github.com/guyAOgreen/Chess-App/issues/3)

## Context

A confirmed game has one canonical PGN, searchable metadata is stored separately,
and derived structures may follow. This ADR decides what is canonical, what is
derived, which fields are relational, and how derived data is prevented from
becoming a competing source of truth.

Two facts shape the decision.

**A `Game` is created by two different paths.** A PGN import starts from text
somebody else wrote. A scoresheet import starts from moves the user confirmed in
review, with no source document at all. Any rule that only works for one path will
leave the table holding rows with two different meanings.

**Our own emitter produces the PGN.** [ADR 0001](0001-java-chess-rules-library.md)
established that `Game.toPgn()` cannot produce canonical PGN — it injects tags that
were not in the input, reorders the tag pair section, and mangles comment spacing.
So canonical PGN is assembled by us in both paths regardless.

The queries this must serve are known: filtering on player, colour, result, date
range and event (#8), games-by-player split by colour and recency (#21), and
position-aware opening statistics (#22). CONTEXT.md is explicit that the position
indexing strategy should be chosen only once realistic query requirements exist.

## Decision

### Moves are canonical; metadata is relational; the source document is provenance

| | Held in | Status |
| --- | --- | --- |
| What was played | `games.movetext` | **Canonical.** Immutable in ordinary application flows. |
| Searchable game metadata | relational columns | **Authoritative** for player identity, game-time player names, event, date, result, ECO, ratings and source. |
| The submitted single-game document | `games.source_pgn` | **Provenance.** Immutable, `NULL` for scoresheet imports. |
| Moves, per-ply FENs, position hashes | nothing | **Computed on read.** Not persisted. |

`movetext` holds validated SAN with move numbers and nothing else — no tag pairs,
and no terminal result token, which assembly appends from the `result` column. The
canonical PGN document is assembled on demand from the metadata columns plus
`movetext`.

**Nothing in the application reads `source_pgn` to answer a product question.** It
exists for audit, for recovering data we do not yet model, and for future features.
If we need a fact from it, we extract that fact into a column with a migration.
M1 accepts one game per import, so `source_pgn` is the exact submitted document for
that game. A future multi-game upload workflow must define its own document-level
provenance rather than copying one upload into every `games` row.

### Why movetext rather than a stored PGN document

The tag pair section is fully determined by the metadata columns. Game-time
`white_name` and `black_name` snapshots supply the `White` and `Black` tags; the
player foreign keys identify the real-world players used for search. Renaming or
merging a `Player` therefore does not silently rewrite historical PGN exports.
Storing an assembled PGN would put derived data (the tags) in the same column as
canonical data (the moves), and correctness would depend on remembering to
regenerate that column on every metadata write. Correcting a misspelled player name
would otherwise leave stored tags silently disagreeing with the authoritative game
metadata.

Storing movetext alone makes that drift structurally impossible: the tags have no
stored form to go stale. The cost is a small assembler that the import endpoint and
any export path call.

This satisfies "a confirmed game has one canonical PGN" — assembly is deterministic,
so a game has exactly one canonical PGN. It is a function rather than a column.

### Canonical PGN assembly

Assembled as:

1. Seven Tag Roster, in the order required by the PGN specification: `Event`,
   `Site`, `Date`, `Round`, `White`, `Black`, `Result`. Unknown string values are
   `?`; an unknown date is `????.??.??`.
2. `WhiteElo`, `BlackElo` and `ECO` when known.
3. A blank line, then `movetext`, then the result token.

The `result` column is authoritative and maps to `1-0`, `0-1`, `1/2-1/2` or `*`.
On PGN import, the `Result` tag and terminal movetext token must agree when both are
present; a conflict rejects the import. If exactly one is present, it supplies the
result; if neither is present, the import is rejected. A checkmate or stalemate
reconstructed from the moves must also agree with the declared result. A decisive
or drawn result in a non-terminal position remains valid because resignation, time
forfeiture and draw agreement are not derivable from the board.

Movetext normalisation comes from the library and is covered by ADR 0001: `O-O`
castling, `=Q` promotion, and check and mate suffixes as emitted by
`MoveList.toSanWithMoveNumbers()`.

**Comments, NAGs and variations are not carried into canonical PGN.** We do not
model them, and emitting variations correctly is real work with no consumer in M1.
They are preserved in `source_pgn`, so nothing is destroyed, but an imported
annotated game replays without its annotations until we model them.

### Schema

```sql
games
  id                UUID        PRIMARY KEY DEFAULT uuidv7()
  white_player_id   UUID        NOT NULL REFERENCES players(id)
  black_player_id   UUID        NOT NULL REFERENCES players(id)
  white_name        TEXT        NOT NULL
  black_name        TEXT        NOT NULL
  white_rating      INT         NULL
  black_rating      INT         NULL
  event             TEXT        NULL
  site              TEXT        NULL
  round             TEXT        NULL
  played_on         DATE        NULL
  result            TEXT        NOT NULL  CHECK (result IN
                        ('WHITE_WON', 'BLACK_WON', 'DRAW', 'UNFINISHED'))
  eco               TEXT        NULL
  source            TEXT        NOT NULL  CHECK (source IN
                        ('PERSONAL', 'CLUB', 'PGN_IMPORT', 'LICHESS',
                         'CHESS_COM', 'MEGA_DATABASE', 'OTHER'))
  movetext          TEXT        NOT NULL
  source_pgn        TEXT        NULL
  created_at        TIMESTAMPTZ NOT NULL
  updated_at        TIMESTAMPTZ NOT NULL

INDEX (white_player_id, played_on DESC)
INDEX (black_player_id, played_on DESC)
INDEX (played_on DESC)
```

Notes on the choices:

- **No JSONB column.** PGN tags we do not model — `TimeControl`, `Termination`,
  `Annotator`, provider-specific tags — remain recoverable from `source_pgn`. A
  JSONB copy would be a third place the same facts could disagree. CLAUDE.md:
  do not default everything to JSON. When a tag earns a query it becomes a real
  column, which is also when we learn how it should be typed.
- **`CHECK` constraints rather than Postgres enum types.** Adding a `source` value
  is then an ordinary migration rather than a type alteration.
- **`round` is `TEXT`.** PGN rounds are not numeric: `1.2` and `?` are both legal.
- **Database-generated UUIDv7 primary keys.** PostgreSQL 18 provides `uuidv7()`.
  Time ordering keeps index locality close to a sequence without exposing a
  countable one in URLs, and generation stays at the persistence boundary so no
  caller has to supply an identifier. Refusing a client-supplied id on the API is a
  separate guarantee, enforced in the API layer — a column default does not prevent
  an explicit insert from choosing one.
- **Player names are game-time snapshots.** `white_player_id` and
  `black_player_id` drive identity and search; `white_name` and `black_name` drive
  canonical PGN assembly. An explicit metadata correction may update a snapshot,
  but an unrelated rename or player merge does not change historical exports.
- **The paired indexes serve #21 directly** — games for a player as a given colour,
  most recent first.
- **`event` is deliberately unindexed**, although #8 filters on it. A personal game
  database is small enough that a scan costs nothing, and an index chosen before we
  know whether the filter is exact-match or prefix-search would likely be the wrong
  one. It is added when a real query is slow, not in advance.

### Rules for derived data

M1 persists no derived move data. The game viewer re-parses `movetext` on read; a
game is 40–80 moves and parsing is microseconds. Data that does not exist cannot
drift, and #22 has not yet produced the query requirements that should shape an
index.

When derived storage is introduced, it must satisfy all four of:

1. **Rebuildable** from `movetext` alone, by exactly one deriver.
2. **Version-stamped** with the deriver version that produced it.
3. **Single-writer** — no other code path may write to it.
4. **Idempotent** — dropping and rebuilding produces identical output.

The test is blunt: **if a derived table cannot be dropped and rebuilt, it is not
derived — it is a second source of truth, and this ADR forbids it.**

### Regeneration

`movetext` is immutable in ordinary create and metadata-update flows. It is never
regenerated from `source_pgn`, and `source_pgn` is never parsed to repair it. For
scoresheet imports there is no source document, so `movetext` is the only record of
the game; applying the same rule to both paths keeps one meaning for the column.

If a confirmed move is later found to be wrong, changing it requires a dedicated,
audited correction use case. That operation replaces the complete validated
`movetext` atomically and rebuilds all derived data. It must not be exposed as a
generic entity setter or happen implicitly while reading or updating metadata.

A defect in the emitter is corrected by an explicit, reviewed one-off migration —
never by an implicit rewrite on read or on write.

## Consequences

- #5 implements this schema and the Flyway migration. #7 calls the assembler when
  returning a created game.
- The canonical PGN assembler is a small, separately testable unit in the `game`
  module, with the PGN specification's tag order and unknown-value conventions as
  its test cases.
- Metadata corrections require no stored PGN rewrite. On-demand assembly uses the
  current authoritative game metadata.
- Player identity changes do not rewrite game-time names; correcting a game-time
  name is a deliberate metadata change on that game.
- A database-generated id means a `Game` has no identifier before it is flushed, so
  #5 must map the column as generated on insert and let the persistence layer read
  the value back rather than assigning one in Java.
- Annotated imports replay without annotations until comments and variations are
  modelled. `source_pgn` holds them in the meantime.
- Opponent preparation (#22) starts from a clean position: it chooses an indexing
  strategy against real queries, and its output is bound by the four derived-data
  rules above.

## Known limitations

- **Partial dates lose precision.** PGN permits `2026.??.??`. `played_on` is `NULL`
  unless the date is fully known, so a year-only game cannot be date-filtered and
  assembly emits `????.??.??`. The original tag survives in `source_pgn` for
  imports. This is acceptable for personal games; partial dates deserve proper
  modelling when reference-game import (M5) makes them common.
- **Scoresheet imports have no provenance document.** `source_pgn` is `NULL`, and
  the confirmed moves are the origin. The recognition audit trail — the model's
  original prediction and the user's corrections — belongs to `GameImport` (#20),
  not to `Game`.

## Not decided here

- **Deduplication.** Whether re-importing the same PGN creates a second row belongs
  to #7, where the import flow is designed.
- **Ownership.** No `user_id` column. Authentication is #25 and unmilestoned; a
  migration adds ownership when it lands.
- **Games with an unknown player.** `[White "?"]` is legal PGN and appears in
  database exports, but `white_player_id` and `black_player_id` are `NOT NULL`, so
  such a game cannot be stored as-is. Whether import rejects it or resolves it to a
  reserved placeholder `Player` belongs to the `Player` model (#4) and the import
  endpoint (#7). M1's workflow is a user importing their own games, which carry
  names; the case becomes real with reference-game import (M5).
- **Multi-game upload provenance.** M1 accepts one game per request. A batch-import
  model will decide whether the original upload belongs in object storage or an
  import-level table rather than duplicating it in `games.source_pgn`.
- **Position indexing.** Deferred to #22 by CONTEXT.md, and bound by the
  derived-data rules above.
- **Comment, NAG and variation modelling.** No consumer yet.
