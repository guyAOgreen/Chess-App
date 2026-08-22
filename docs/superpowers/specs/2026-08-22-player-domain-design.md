# Player domain model and persistence

Date: 2026-08-22

Issue: [#4](https://github.com/guyAOgreen/Chess-App/issues/4) — M1, Game database

Chess terminology used here is defined in the [glossary](../../glossary.md).

## Goal

Introduce `Player` as the first domain module in the core service: a real-world
chess player, who need not be an application user. Opponent preparation is mostly
concerned with players who have never used the application.

`Game` (#5) depends on this for its foreign keys, and PGN import (#7) depends on it
to resolve player names into rows.

## Why this is more than one entity

There is no domain code in the repository yet — the backend is scaffolding plus one
context test. This change therefore fixes conventions that every later module
copies: how domain types relate to JPA entities, where the repository interface
lives, how migrations are named, and how unit and integration tests are separated.
Those conventions are the expensive part of this issue; the four fields are not.

## Decisions

### 1. The domain model is separate from the JPA entity

A pure `Player` in `domain/`, a `PlayerEntity` confined to `persistence/`, and
mapping between them. The domain package depends on neither `jakarta.persistence`
nor `org.springframework`.

This costs more files than annotating one class, and for four fields that cost is
real. It is taken because of what comes next:
[ADR 0002](../../adr/0002-game-storage.md) requires `movetext` to be write-once and
explicitly forbids exposing that change as a generic entity setter. A JPA entity
needs a no-arg constructor and mutable fields, so on a single-class model that rule
has nothing structural behind it — only discipline. Establishing the separation
here, where the model is trivial, is cheaper than retrofitting it in #5 where it
matters.

### 2. No REST endpoints in this change

Issue #4 names the `api / application / domain / persistence` layering. That is read
as naming the convention, not as requiring a controller: nothing consumes a player
endpoint yet. Player search is #21 in M4, and the games list (#10) shows player
names carried on the game rather than fetched from a player resource.

The `api/` package is created when something needs it.

### 3. Identity is exact display name, enforced by a unique constraint

`display_name` is unique. `fide_id` is unique when present. Import trims the PGN tag
value and matches exactly, creating only when there is no match.

The alternative — allowing duplicate rows and merging later — is truer to the world,
since two real people can share a name. It was rejected for M1 because it ships a
known-duplicates problem with no merge tooling to resolve it, and because a unique
constraint makes correctness structural rather than dependent on every write path
checking first.

The cost is recorded under Known limitations.

### 4. An unknown player is rejected, and `?` cannot be a name

`display_name` is `NOT NULL`, non-blank, and cannot be the PGN unknown marker `?`.
An import whose `White` or `Black` tag is unknown is rejected with a clear error.

This follows directly from decision 3. With `display_name` unique, permitting `?`
would collapse every unknown player from every import into a single shared `Player`
— the placeholder design, created implicitly by whichever import ran first, with
nothing documenting that it exists. If a placeholder is ever wanted it should be
introduced deliberately, not as a side effect.

[ADR 0002](../../adr/0002-game-storage.md) deferred this question here. It becomes
pressing again with reference-game import (M5), where unknown players are common.

## Package layout

```text
com.chessapp.player
├── domain/
│   ├── Player.java                   record; no jakarta.*, no spring.*
│   └── PlayerRepository.java         interface expressed in domain types
├── application/
│   └── FindOrCreatePlayer.java       use case consumed by #7
└── persistence/
    ├── PlayerEntity.java             @Entity
    ├── PlayerJpaRepository.java      Spring Data
    └── PlayerRepositoryAdapter.java  implements PlayerRepository
```

The repository interface lives in `domain/` and is implemented in `persistence/`,
so the dependency arrow points inward. Persistence types are package-private
wherever Spring allows it, so nothing outside the package can reference an entity.

## Domain model

`Player` is a record of `id`, `displayName`, `fideId`, `federation`, validated in a
compact constructor:

- `displayName` — required, trimmed before validation, non-blank, not `?`
- `fideId` — null, or digits only
- `federation` — null, or exactly three uppercase letters
- `id` — required and non-null

Invalid input throws. A `Player` that exists is a valid `Player`.

**The domain never holds an unsaved `Player`.** Creation runs through
`FindOrCreatePlayer`, which returns a persisted instance, so `id` is never null and
the record needs no half-constructed state. This is what makes an immutable domain
model practical.

The trimmed value is what gets stored, not merely what gets validated.

No `FideId` or `Federation` value types. Four fields do not justify them, and
CLAUDE.md is explicit about not building abstractions ahead of need.

### Repository interface

Because the domain holds no unsaved `Player`, the repository cannot expose a
`save(Player)` that takes one without an id. It offers instead:

```java
Optional<Player> findByDisplayName(String displayName);
Player create(String displayName, String fideId, String federation);
```

`create` performs the insert and returns the persisted `Player`, id included. The
caller never constructs an identifier, which is what keeps `Player.id` non-null and
the record immutable.

Matching is exact and **case-sensitive** after trimming, which is what the unique
index enforces. `Green, Guy` and `green, guy` are therefore two different players.
Case-insensitive matching is deliberately not attempted: normalising names is the
alias problem, and a half-measure here would be harder to reason about than none.

## Migration

`services/core/src/main/resources/db/migration/V1__create_players.sql`:

```sql
CREATE TABLE players (
    id           UUID        PRIMARY KEY DEFAULT uuidv7(),
    display_name TEXT        NOT NULL,
    fide_id      TEXT        NULL,
    federation   TEXT        NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT players_display_name_not_blank   CHECK (btrim(display_name) <> ''),
    CONSTRAINT players_display_name_not_unknown CHECK (display_name <> '?'),
    CONSTRAINT players_fide_id_digits           CHECK (fide_id IS NULL OR fide_id ~ '^[0-9]+$'),
    CONSTRAINT players_federation_format        CHECK (federation IS NULL OR federation ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX players_display_name_key ON players (display_name);
CREATE UNIQUE INDEX players_fide_id_key      ON players (fide_id) WHERE fide_id IS NOT NULL;
```

Notes:

- `fide_id` is `TEXT`. It is an identifier, never arithmetic, and text makes no
  assumption about leading zeros or a future change of format.
- `uuidv7()` requires PostgreSQL 18, which `infra/docker-compose.yml` and the
  Testcontainers setup both already pin.
- The validation rules are deliberately expressed twice, in the domain and in the
  database. The domain produces a good error message; the constraint makes the rule
  true no matter which code path writes.
- `spring.jpa.hibernate.ddl-auto` is already `validate`, so the entity mapping and
  this migration must agree or the context fails to start.

## Application layer

`FindOrCreatePlayer` accepts a display name plus optional FIDE ID and federation,
and returns an existing or newly created `Player`.

It must handle the insert race explicitly. Two concurrent imports naming the same
opponent both miss on read, and one loses on the unique index; the use case catches
that constraint violation and re-reads rather than propagating a failure. This is
behaviour, not plumbing, so it is tested.

Conflict between supplied and stored `fideId` or `federation` is out of scope: this
change matches on name and returns what is stored. Reconciling differing attributes
belongs with the alias work.

## Testing

Following the split the scaffolding established — `*Test` under surefire, `*IT`
under failsafe with Testcontainers, as `ApplicationContextIT` does.

| Test | Kind | Covers |
| --- | --- | --- |
| `PlayerTest` | unit | every validation rule, including `?`, blank, whitespace trimming, federation and FIDE ID formats |
| `PlayerRepositoryIT` | integration | round-trip; database-generated id is populated; both unique indexes reject duplicates; each CHECK constraint rejects bad input |
| `FindOrCreatePlayerIT` | integration | creates on first call, matches on the second, and recovers from a duplicate insert |

Test-driven, per CLAUDE.md: each behaviour gets a failing test first.

## Risks

**Reading back a database-generated id.** ADR 0002 chose `DEFAULT uuidv7()`, so
Hibernate must read the value after insert rather than assigning it. This is the one
part of the change whose mapping cannot be predicted from reading the codebase — it
either works cleanly or needs a different annotation strategy.

Mitigation: the first thing built is a failing integration test proving a saved
entity comes back with a populated id. If the mapping needs a different approach,
that surfaces before any domain code is written. If it proves genuinely awkward, the
fallback is generating the UUIDv7 in Java, which would be a deviation from ADR 0002
worth recording rather than making silently.

## Known limitations

- **Two real people sharing a name become one `Player`.** A consequence of decision
  3. Rare in a personal game database, and ADR 0002's game-time name snapshots mean
  historical PGN exports remain correct either way. Splitting a conflated player is
  harder than merging duplicates, so this is a real trade rather than a free one; it
  is revisited when aliases arrive.
- **Games with an unknown player cannot be imported.** A consequence of decision 4,
  and acceptable while M1's workflow is a user importing their own games. Reference
  databases (M5) will force the question.

## Out of scope

- Aliases: FIDE name, alternative spellings, Lichess and Chess.com usernames.
- REST endpoints, and therefore DTOs and API tests.
- Merge and split tooling.
- Ownership, which arrives with authentication (#25).
- Reconciling conflicting attributes for a matched player.
