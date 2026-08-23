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
│   ├── Player.java                   persisted domain record
│   ├── NewPlayer.java                validated creation values; no id
│   ├── PlayerRepository.java         interface expressed in domain types
│   └── PlayerIdentityConflict.java   unchecked; contradictory identity data
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

`Player` is a record of `id`, `displayName`, `fideId`, `federation`. `NewPlayer` is
a record of the same three descriptive fields without an id. Both use the same
package-private validation and normalisation functions, so creation and rehydration
cannot acquire different rules:

- `displayName` — required, trimmed before validation, non-blank, not `?`
- `fideId` — null, or digits only
- `federation` — null, or exactly three uppercase letters
- `id` — required and non-null

Invalid input throws. Constructing `NewPlayer` therefore validates and normalises
the values before persistence is called. Constructing `Player` validates the values
again when a row is mapped back into the domain, protecting the domain from corrupt
or unexpectedly shaped stored data. A `Player` that exists is a valid `Player`.

**The domain never holds an unsaved `Player`.** `NewPlayer` represents a validated
creation request rather than an entity with a temporarily null identity. Creation
runs through `FindOrCreatePlayer`, which returns a persisted `Player`, so
`Player.id` is never null and the entity needs no half-constructed state.

The trimmed value is what gets stored, not merely what gets validated.

No `FideId` or `Federation` value types. Four fields do not justify them, and
CLAUDE.md is explicit about not building abstractions ahead of need.

### Repository interface

Because the domain holds no unsaved `Player`, the repository does not expose a
`save(Player)` that takes one without an id. It offers instead:

```java
Optional<Player> findByDisplayName(String displayName);
Player createOrFind(NewPlayer candidate);
```

`createOrFind` establishes identity by display name and returns the persisted
`Player`, id included. The caller never constructs an identifier, which keeps
`Player.id` non-null and the record immutable. The name also makes its concurrency
semantics explicit; this is not a generic CRUD save operation.

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
    CONSTRAINT players_display_name_trimmed     CHECK (display_name = btrim(display_name)),
    CONSTRAINT players_display_name_not_blank   CHECK (btrim(display_name) <> ''),
    CONSTRAINT players_display_name_not_unknown CHECK (display_name <> '?'),
    CONSTRAINT players_fide_id_digits           CHECK (fide_id IS NULL OR fide_id ~ '^[0-9]+$'),
    CONSTRAINT players_federation_format        CHECK (federation IS NULL OR federation ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX players_display_name_idx ON players (display_name);
CREATE UNIQUE INDEX players_fide_id_idx      ON players (fide_id) WHERE fide_id IS NOT NULL;
```

Notes:

- `fide_id` is `TEXT`. It is an identifier, never arithmetic, and text makes no
  assumption about leading zeros or a future change of format.
- `uuidv7()` requires PostgreSQL 18, which `infra/docker-compose.yml` and the
  Testcontainers setup both already pin.
- The validation rules are deliberately expressed twice, in the domain and in the
  database. The domain produces a good error message; the constraint makes the rule
  true no matter which code path writes.
- Requiring `display_name = btrim(display_name)` makes database uniqueness match the
  domain's exact-after-trimming identity rule. It also ensures values such as ` ? `
  cannot bypass the unknown-player check through a direct database write.
- `spring.jpa.hibernate.ddl-auto` is already `validate`, so the entity mapping and
  this migration must agree or the context fails to start.

## Application layer

`FindOrCreatePlayer` accepts a display name plus optional FIDE ID and federation,
constructs a `NewPlayer` (thereby validating and normalising before any database
operation), and returns an existing or newly created `Player`.

The persistence adapter handles the insert race without using an exception as
control flow. It executes a native insert with:

```sql
ON CONFLICT (display_name) DO NOTHING
```

and then reads by `display_name`. These must be two statements: after a concurrent
insert commits, PostgreSQL gives the second statement a fresh snapshot in which the
winning row is visible. Because the display-name conflict does not abort or mark the
transaction rollback-only, the read is safe. The adapter returns the inserted row
when it won and the existing row otherwise.

**This depends on `READ COMMITTED` isolation**, which is PostgreSQL's default and
what the application uses. It is a requirement rather than an observation: under
`REPEATABLE READ` the second statement reuses the snapshot taken by the first, so a
row committed by the concurrent winner would be invisible and the read would come
back empty. Any future change to the transaction isolation of this path breaks it,
so the constraint is asserted by the concurrency test rather than left implicit.

`ON CONFLICT` targets only `display_name`. A collision on `fide_id` for a different
display name is not a find-or-create race; it means the supplied identity data is
inconsistent. The adapter translates that specific constraint violation into
`PlayerIdentityConflict` and does not attempt a display-name re-read.

`PlayerIdentityConflict` is an unchecked exception and lives in `domain/`, not
`application/`. It is thrown by the persistence adapter, and an adapter may depend
on the domain but not on the application layer — putting it in `application/` would
reverse the dependency direction this design states above. Unchecked because there
is nothing a caller can usefully do to recover: the input contradicts stored data,
so it becomes a rejected import rather than a retry. #7 maps it to an HTTP error.

Conflict between supplied and stored `fideId` or `federation` is out of scope: this
change matches on name and returns what is stored. Reconciling differing attributes
belongs with the alias work.

## Testing

Following the split the scaffolding established — `*Test` under surefire, `*IT`
under failsafe with Testcontainers, as `ApplicationContextIT` does.

| Test | Kind | Covers |
| --- | --- | --- |
| `PlayerTest` | unit | `NewPlayer` validates before persistence; `Player` validates rehydrated data; `?`, blank, whitespace trimming, federation and FIDE ID formats |
| `PlayerRepositoryIT` | integration | round-trip; database-generated id is populated; display-name upsert returns one identity; duplicate FIDE ID is distinguished; `findByDisplayName` matches on the trimmed argument |
| `PlayerSchemaIT` | integration | every CHECK constraint, including stored trimming, rejects bad input directly against the schema, independent of the JPA/adapter layer |
| `FindOrCreatePlayerIT` | integration | creates on first call, matches on the second, two genuinely concurrent calls return the same player, and a FIDE-ID collision throws `PlayerIdentityConflict` |

Test-driven, per CLAUDE.md: each behaviour gets a failing test first.

### The concurrency test

The concurrent case is the one test here that is easy to write badly. A version
built on `Thread.sleep` and hope passes by luck, fails occasionally, and teaches
everyone to re-run the build rather than read the failure.

It must instead use two threads on **separate database connections**, released
together by a `CountDownLatch` so both perform their initial read before either
inserts, and it must assert that both calls return the *same* player id — not
merely that neither threw. Without separate connections there is no race to test,
because a single connection serialises the statements.

This test exercises the `READ COMMITTED` assumption above, but it cannot pin it by
itself: it calls `createOrFind` with no outer transaction open, so `createOrFind`
always starts its own transaction and the isolation attribute always applies. A
regression that only breaks when a caller joins an existing transaction at a
different isolation level — the case PGN import (#7) introduces — would not be
visible to this test at all. That gap is closed structurally rather than by a test:
`PlayerPersistenceConfiguration` configures the transaction manager to validate
existing transactions, so a caller joining `createOrFind` with a mismatched
isolation level now fails fast with `IllegalTransactionStateException` instead of
silently running under the wrong guarantee.

## Risks

**Database-generated ids, largely defused.** ADR 0002 chose `DEFAULT uuidv7()`, and
an earlier draft of this design treated "will Hibernate read the generated value
back after an insert?" as the main unknown. The native upsert removes most of that
exposure: the insert is a native statement and the id arrives via the subsequent
read, so Hibernate is never asked to populate a generated identifier on flush.

What remains is narrower. `PlayerEntity` must still map `id` as database-generated
so that `ddl-auto: validate` passes and no code path assigns one, and any later use
of a plain `save()` on a new entity would reintroduce the original question. The
`PlayerRepositoryIT` round-trip covers the mapping; anything beyond it is deferred
to whichever change first needs a conventional insert.

Generating UUIDv7 in Java is **not** the fallback it was described as in the earlier
draft. It would deviate from ADR 0002 to solve a problem this design no longer has.

**The upsert is PostgreSQL-specific.** `ON CONFLICT` is not portable SQL, so the
adapter uses a native query and is tested against a real PostgreSQL container rather
than an in-memory database. That is already how `ApplicationContextIT` works, and
CLAUDE.md names PostgreSQL as the primary transactional database, so this is an
accepted consequence rather than an open question.

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
