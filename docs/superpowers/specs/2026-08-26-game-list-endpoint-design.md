# GET /games — list with filtering and pagination

Date: 2026-08-26

Issue: [#8](https://github.com/guyAOgreen/Chess-App/issues/8) — M1, Game database

Chess terminology used here is defined in the [glossary](../../glossary.md).

## Goal

List stored games with filtering on the searchable metadata — player, colour,
result, date range, event — and pagination, filtering on relational columns rather
than scanning PGN text.

The domain, the persistence layer and the HTTP conventions all exist already:
[#5](https://github.com/guyAOgreen/Chess-App/issues/5) with
[ADR 0002](../../adr/0002-game-storage.md) put every filterable value in its own
column, and [#7](2026-08-25-pgn-import-endpoint-design.md) established the `api`
package, the problem-details convention and the `/api` mounting.

What is new is that this is the first endpoint whose **query varies with the
request**. Everything before it ran fixed statements binding every value as a named
parameter. A list endpoint cannot: which `WHERE` clauses exist depends on which
filters were supplied, and `ORDER BY` cannot be a bound parameter at all. A sort
field arriving from the request and dropped into the query is string concatenation
whatever it is called. So the shape of the query has to come from a whitelist, and
only values ever bind.

The contract matters for the same reason #7's did:
[#10](https://github.com/guyAOgreen/Chess-App/issues/10) consumes it,
[#21](https://github.com/guyAOgreen/Chess-App/issues/21) extends it, and
[#27](https://github.com/guyAOgreen/Chess-App/issues/27) codifies it.

## Decisions

### 1. Offset pagination with a total count

`?page=0&size=25`, and the response carries `totalElements` and `totalPages`.

Keyset pagination is stable under concurrent inserts and stays fast at any depth,
and it is the right answer for a large or fast-moving dataset. It is the wrong
answer here. It gives no total and no jump-to-page-6, so the games list becomes
infinite scroll or next/previous only; and every sort option needs its own cursor
encoding, which is real machinery in support of a personal database that will hold
hundreds of games rather than millions.

The costs of offset paging are a second `COUNT` per request, and the possibility
that a game inserted mid-browse shifts a row across a page boundary. Both are
acceptable at this scale, and neither is invisible: they are recorded under
"Known limitations" so that the day the dataset outgrows them, the reason the
choice was made is on the record rather than reconstructed.

`size` is capped at 100 and defaults to 25. `page` is capped too — see decision 11.

### 2. The sort whitelist is a type, not a check

`sort` binds to a `GameSort` enum with one value, `PLAYED_ON`. `direction` binds to
a `SortDirection` enum of `ASC` and `DESC`.

This is the whole answer to the issue's central constraint. An unknown sort field
is not rejected by a validation rule that someone has to remember to write and keep
current — it is *unrepresentable*. `?sort=movetext` and `?sort=id; DROP TABLE games`
both fail in Spring's `String`-to-enum conversion, before a line of our code runs,
and come back as 400 problem+json through the handler #7 already enabled. The
mapping from `PLAYED_ON` to a column lives in one `switch` in persistence, so the
set of orderable columns is enumerable by reading a single file.

Spring Data's `Sort` is deliberately not used at the boundary. It accepts arbitrary
property names by design, which would put us back to validating a string.

One value looks thin. It is the honest one: `played_on` is the only column with a
supporting index, and #10 asks for filtering controls rather than sortable columns.
Adding a value is one enum constant plus one `switch` arm, and the mechanism that
makes adding it safe exists from the first commit — which is what #21 inherits.

### 3. A list row is a projection, not a whole game

`GameResponse`'s javadoc left this open for this issue. The answer is a separate
`GameSummary` in the domain and `GameSummaryResponse` in the API, carrying the
public list metadata but neither `movetext` nor the provenance-only `sourcePgn`.

A page of 25 games would otherwise ship 25 complete move lists to render a table
that displays none of them. That is the visible half. The invisible half is that
`movetext` is a `TEXT` column, so a longer game's value is TOASTed out of line and
reading it costs a separate fetch — 25 of them, per list request, discarded by the
mapper.

So the projection goes all the way down: the Criteria query `multiselect`s the
columns the summary needs, and `movetext` never leaves the database. Reusing `Game`
and dropping the field at the API layer would have been less code and would have
made the decision a habit rather than a structure.

`GameSummary` keeps `Game`'s shape, holding a `GameSide` per colour rather than six
flat columns, so the two types read the same way. That means the query selects a
`Tuple` and a private mapper in `GameSearchQuery` assembles the record, rather than
`cb.construct` building it directly: the JPA specification does not permit a
compound selection as an argument to another, so a nested `construct` for the two
`GameSide` values is not portable. The mapper is a dozen lines and keeps the
awkwardness in persistence, where it belongs.

`GameResponse` is unchanged and remains the detail representation for
[#9](https://github.com/guyAOgreen/Chess-App/issues/9), so the viewer still gets
`movetext` when it opens a game.

The cost is a second domain type that must stay in step with `Game`. It is bounded:
both are records over the same columns, and a column added to one and forgotten in
the other shows up as a missing field in the list response.

### 4. The player filter takes an identifier, not a name

`?playerId=<uuid>`, optionally narrowed by `?colour=WHITE|BLACK`.

This is the exact shape `games_white_player_played_on_idx` and
`games_black_player_played_on_idx` were created for — the V2 migration comment says
so in as many words. Supplied without `colour` it becomes
`white_player_id = :id OR black_player_id = :id`, which PostgreSQL serves as a
BitmapOr across both indexes rather than degrading to a scan.

Filtering by name was considered and rejected for now. It would match `white_name`
and `black_name`, which are unindexed *game-time snapshots* rather than identity —
ADR 0002 gave them their own columns precisely so that renaming a player does not
rewrite history, which also means a renamed player's older games would answer to
the old name and not the new one. And name matching is about to change: #21 makes
it case-insensitive and alias-aware. Shipping a name filter here would publish
semantics that #21 immediately has to redefine.

The consequence is that the filter is not usable from a text box until #21 exists.
#10 either offers a player pick-list fed by a player-listing endpoint or ships
without the player filter. That is called out as a dependency rather than worked
around.

### 5. `event` matches case-insensitively on a substring, with metacharacters escaped

`?event=championship` matches `Obs Club Championship`.

The V2 migration comment left `event` unindexed and recorded why: "the right index
depends on whether the filter turns out to be exact-match or prefix-search". The
answer is neither. Exact match is only usable behind a pick-list of known event
names, which nothing builds; prefix match asks a user to remember how an event name
begins, which they do not. A free-text box means *contains*.

That brings forward the wrinkle #21 warns about. `%` and `_` are metacharacters
inside a `LIKE` pattern, so the user's input must be escaped before it becomes one.
This is not injection — the value still binds — but unescaped, a search for `_`
matches every single-character event and a leading `%` in the input forces work
nobody asked for. The escape character is the backslash, and escaping replaces the
backslash **first**, then `%` and `_`; reversing that order double-escapes the
escapes. The query names its escape character explicitly rather than relying on a
default.

`cb.like(cb.lower(...), pattern, escape)` is used rather than PostgreSQL's `ILIKE`:
Hibernate renders it as `lower(event) like ? escape '\'`, which is the same plan and
stays inside portable Criteria.

Two consequences are behaviour rather than implementation, so they belong in the
contract. A game whose `event` is null never matches an event filter, because
`null like ?` is null rather than false. And case folding happens twice in two
places — Java's `toLowerCase(Locale.ROOT)` on the input, PostgreSQL's `lower()` on
the column — which are separately collation-dependent and could in principle
disagree on non-ASCII input. Acceptable for event names; recorded so it is not
rediscovered as a bug.

### 6. Nulls sort last in both directions, and ascending pays for it

`played_on` is null whenever a PGN date was only partly known, and PostgreSQL sorts
nulls *first* under `DESC`. The three indexes are declared `DESC NULLS LAST`
precisely so undated games do not lead the list, and the issue is explicit that an
`ORDER BY` disagreeing with the index cannot be served by it.

Descending therefore orders `played_on DESC NULLS LAST`, matching
`games_played_on_idx` exactly.

Ascending orders `played_on ASC NULLS LAST`, which the index **cannot** serve. A
backwards scan of a `DESC NULLS LAST` index yields `ASC NULLS FIRST`, so ascending
falls back to a sort. The alternative — flipping to nulls-first under `ASC` to keep
the index — was rejected: undated games would jump from the bottom of the list to
the top when a user clicks to reverse the order, which is a worse thing to explain
than a sort over a few hundred rows.

Jakarta Persistence 3.2 added null precedence to the Criteria API
(`CriteriaBuilder.desc(Expression, Nulls)`), verified against
`jakarta.persistence-api-3.2.0.jar` as resolved by this build alongside Hibernate
7.4.1. So this is expressible in portable Criteria and needs neither native SQL nor
a Hibernate-specific builder.

Ordering is tie-broken on `id`, in the same direction. Without a tie-break, two
games sharing a `played_on` have no defined relative order, and paging over them can
repeat one row and skip another. `id` is a `uuidv7()`, so it is time-ordered and the
tie-break reads as "most recently imported first" under `DESC` rather than as an
arbitrary disambiguator. PostgreSQL can serve the combination with an incremental
sort over the index.

### 7. A date bound excludes undated games

`?from=2026-01-01` emits `played_on >= :from`, which no null row satisfies. A user
filtering to 2026 therefore does not see a game whose PGN said `2026.??.??`.

This is correct SQL and, on reflection, correct behaviour: a game that might have
been played in 2025 does not belong in a 2026 list. It is also exactly the kind of
thing that gets reported as a bug, so it is stated in the contract. An
`includeUndated` flag was considered and rejected as a parameter in search of a
complaint.

### 8. Hand-built Criteria, not Specification and not native SQL

The variable query is assembled in a dedicated `GameSearchQuery` in persistence,
holding an `EntityManager` and building predicates and ordering from the domain
query types.

Native SQL with whitelist-assembled fragments was rejected. It would match the
existing native insert and give total control, but it is literal structural string
assembly — safe under a whitelist, and still the pattern the issue is warning about
— and it means two statements, rows and count, kept in step by hand.

Spring Data `Specification` with `JpaSpecificationExecutor` was the close call. It
is less code and the count query comes free. It was rejected on two grounds. It
rests on Spring Data's translation of `Sort.NullHandling` into Criteria producing
`NULLS LAST`, which would have to be verified rather than read; and `Page` and
`Pageable` pull toward the domain, which `CLAUDE.md` asks to keep free of Spring, so
the mapping we would write anyway claws back most of the saving. This also follows
the grain of a codebase that already declines Spring Data conveniences with a stated
reason — `Repository<>` rather than `JpaRepository`, a native insert rather than
`save()`.

The count query is the one real cost, because a rows query and a count query that
disagree are a silent wrong answer. Predicate construction is therefore defined
once, by a method taking `(CriteriaBuilder, Root<GameEntity>)`, and invoked for
both query roots.

### 9. No application-layer class on the read path

`GameController` binds and validates `GameListParams`, converts them to a
`GameQuery`, and maps the resulting `GamePage` to a `GamePageResponse`. Both
conversions are DTO work, which `CLAUDE.md` assigns to the API layer. There is no
orchestration, no transaction to own and no interaction between domain objects to
coordinate.

A `ListGames` whose `execute` is a single delegating line would be a home for
nothing, which is the reasoning #7 used to decline a `shared` package. The class
arrives with #21, when resolving a player name to a `Player` before building the
query gives it work to do.

The controller consequently depends on `GameRepository` directly. That is worth
being precise about, because the obvious objection is the wrong one: `GameRepository`
is declared in `com.chessapp.game.domain` and its javadoc records that the
dependency points inward, so this is the API layer depending on a **domain** port,
not on persistence. What it does skip is the application layer, which every other
flow passes through.

That inconsistency is accepted rather than argued away, and it is worth revisiting
as a question in its own right — whether read paths in this codebase should have
application-layer use cases on principle, or only when they have something to
orchestrate. That decision governs #9, #21 and every later read endpoint, so it
belongs in an issue of its own rather than being settled as a side effect of the
first list endpoint. It is the first thing to change if this path acquires any
behaviour at all.

**The transaction boundary is the adapter's, as it is for every other read.**
`GameRepositoryAdapter.find` is `@Transactional(readOnly = true)`, matching
`findById` immediately above it and `PlayerRepositoryAdapter`. It is there for one
connection checkout instead of two and to keep Hibernate from dirty-checking, and
explicitly **not** for snapshot consistency: under PostgreSQL's default
`READ COMMITTED` isolation each statement takes its own snapshot, so wrapping the
rows and count queries in one read-only transaction does not make them agree. See
"Known limitations".

### 10. Our own page envelope, not a serialised `PageImpl`

The response is a `GamePageResponse` record of `content`, `page`, `size`,
`totalElements` and `totalPages`.

Spring Data warns that serialising `PageImpl` directly produces a shape that is not
stable across versions. Under decision 8 we never hold one, so this is mostly a note
for anyone tempted to reintroduce it. `totalPages` is derived arithmetic and lives
in the response record rather than in the domain `GamePage`, which stays the
minimum: content, page, size and total.

### 11. Cross-field validation at the boundary, and again in the domain

Query parameters bind into one `@Valid` record, `GameListParams`, by constructor
binding. Single-field rules are annotations — `@Min(0)` on `page`, `@Min(1)` and
`@Max(100)` on `size`, and `@Size(max = 255)` on `event`. A supplied event is
trimmed; an empty result is treated as omitted, so `?event=` has the same meaning
as no event filter rather than accidentally matching every non-null event. The two
cross-field rules are `@AssertTrue` methods on the same record:

* `colour` requires `playerId`. A colour on its own has nothing to constrain, and a
  filter that silently does nothing is worse than one that is refused.
* `from` must not be after `to`. The request is unsatisfiable, so a client bug is
  worth naming rather than answering with an empty list.

Both fail as 400 problem+json.

`page` also carries `@Max(100_000)`. `setFirstResult` takes an `int`, so a large
enough `page` overflows `page * size` to a negative and produces a 500. With `size`
capped at 100 the bound keeps the product below ten million, comfortably inside
range, and no real dataset approaches it.

Defaults are applied once, by `GameListParams`, so an omitted `sort`, `direction`,
`page` or `size` has become a concrete value before a `GameQuery` exists.
`GameQuery` therefore requires all four to be non-null and does not carry defaults
of its own — two places deciding what "no sort given" means is how they come to
disagree.

`GameQuery`'s compact constructor re-checks the same two cross-field rules. This is
the posture #7 took with domain construction: bean validation is the input gate, and
a domain rejection reaching a caller means a defect in the gate rather than bad
input. It is cheap, and it means the invariant holds for any future caller that is
not this controller.

## Package layout

```text
com.chessapp.game
├── api/
│   ├── GameController.java        + GET /api/games
│   ├── GameListParams.java        record; @Valid, constructor-bound query params
│   ├── GameSummaryResponse.java   record; from(GameSummary)
│   └── GamePageResponse.java      record(content, page, size, totalElements, totalPages)
│
├── domain/
│   ├── GameQuery.java             validated criteria
│   ├── GameSort.java              enum: PLAYED_ON            ← the whitelist
│   ├── SortDirection.java         enum: ASC | DESC
│   ├── GameColour.java            enum: WHITE | BLACK        ← new
│   ├── GameSummary.java           list projection; no movetext, no sourcePgn
│   ├── GamePage.java              record(List<GameSummary>, page, size, totalElements)
│   └── GameRepository.java        + GamePage find(GameQuery)
│
└── persistence/
    ├── GameSearchQuery.java       Criteria: predicates, ordering, count
    ├── LikePattern.java           LIKE metacharacter escaping (package-private)
    └── GameRepositoryAdapter.java + find(query), @Transactional(readOnly = true)
```

`GameSummaryResponse` reuses `GameResponse.Side` rather than redeclaring an
identical nested record; they are the same concept in the same package.

`GameSort` is a bare enum in the domain. Which column backs a sort is a persistence
fact, so the mapping from `PLAYED_ON` to an attribute lives in `GameSearchQuery`.

`SortDirection` is generic enough to belong in a `shared` package eventually. It
stays in `game/domain` until a second module needs it, rather than creating the
package for one enum.

Nothing is added to `application/`, per decision 9.

## The endpoint contract

### Request

```http
GET /api/games?playerId=0199f3c1-…&colour=WHITE&result=DRAW
              &from=2026-01-01&to=2026-06-30&event=championship
              &sort=PLAYED_ON&direction=DESC&page=0&size=25
```

| Parameter | Type | Default | Notes |
| --- | --- | --- | --- |
| `page` | int | `0` | 0–100 000 |
| `size` | int | `25` | 1–100 |
| `sort` | `GameSort` | `PLAYED_ON` | the whitelist |
| `direction` | `SortDirection` | `DESC` | nulls last either way |
| `playerId` | UUID | — | either colour unless `colour` is given |
| `colour` | `WHITE` or `BLACK` | — | requires `playerId` |
| `result` | `GameResult` | — | `WHITE_WON`, `BLACK_WON`, `DRAW`, `UNFINISHED` |
| `from` | ISO date | — | inclusive; excludes undated games |
| `to` | ISO date | — | inclusive; excludes undated games |
| `event` | string (max 255 characters) | — | trimmed, case-insensitive substring; blank means omitted |

Every filter supplied is combined with `AND`. Every filter omitted constrains
nothing.

### Success

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "content": [
    {
      "id": "0199f3c1-…",
      "white": { "playerId": "…", "name": "Green, Guy",     "rating": 1834 },
      "black": { "playerId": "…", "name": "Adams, Michael", "rating": 2680 },
      "event": "Obs Club Championship",
      "site": null,
      "round": "3",
      "playedOn": "2026-03-14",
      "result": "BLACK_WON",
      "eco": "B90",
      "source": "PGN_IMPORT"
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 143,
  "totalPages": 6
}
```

No `movetext` key, per decision 3. Null-valued optional metadata is present as
`null` rather than omitted, matching #7 — a client sees one shape whatever the
document said.

No matches is `200` with an empty `content` and `totalElements: 0`, not `404`. The
collection exists; it is the selection that is empty. A `page` past the last one is
the same: empty `content`, with `totalElements` still reporting the filtered total.

### Failure

| Case | Status |
| --- | --- |
| Unknown `sort`, `direction`, `colour` or `result` value | 400 |
| Malformed `playerId`, `from` or `to` | 400 |
| `size` outside 1–100, `page` outside 0–100 000 | 400 |
| `event` longer than 255 characters | 400 |
| `colour` without `playerId` | 400 |
| `from` after `to` | 400 |
| Domain construction fails | 500 |

Every failure body is `application/problem+json`.

There is no 422 here. #7 drew the line at "the request was understood and the
content was the problem", and a list request carries no content — a parameter that
cannot be interpreted is a malformed request, which is 400.

The 500 row is a bug, not an input case, and is listed for the same reason #7 listed
it: decision 11 argues it cannot be reached, so a future change that makes it
reachable should read as a regression rather than as new behaviour.

## Query construction

```text
GameSearchQuery.run(GameQuery)

  predicates(cb, root) ──┬──► rows:  CriteriaQuery<Tuple>
                         │              multiselect(id, white…, black…, event,
                         │                          site, round, playedOn,
                         │                          result, eco, source)
                         │              orderBy(sort, direction, then id)
                         │              setFirstResult(page * size)
                         │              setMaxResults(size)
                         │                  │
                         │                  └─► map each Tuple → GameSummary
                         │
                         └──► count: CriteriaQuery<Long>
                                        select(cb.count(root))

                              ──► GamePage(content, page, size, totalElements)
```

Predicate construction is defined once and invoked for each query root. Criteria
`Predicate` instances themselves cannot be shared between the rows and count
queries, but both receive their structure from the same helper so their filtering
logic cannot drift.

| Filter | Predicate |
| --- | --- |
| `playerId`, no `colour` | `cb.or(equal(whitePlayerId, id), equal(blackPlayerId, id))` |
| `playerId` + `WHITE` | `cb.equal(whitePlayerId, id)` |
| `playerId` + `BLACK` | `cb.equal(blackPlayerId, id)` |
| `result` | `cb.equal(result, value)` — bound as the enum name, matching the `TEXT` column |
| `from` | `cb.greaterThanOrEqualTo(playedOn, from)` |
| `to` | `cb.lessThanOrEqualTo(playedOn, to)` |
| `event` | `cb.like(cb.lower(event), pattern, escape)` |

Only values bind. The *structure* — which predicates exist, which column and
direction the ordering uses — comes from the domain query types, and those come from
enums and typed fields rather than from strings.

`colour` without `playerId` cannot reach here: decision 11 rejects it at the
boundary and `GameQuery` rejects it again.

## Testing

### `LikePatternTest`

The escaping is the one piece of pure logic worth isolating from the database.

* an ordinary word becomes a contains-pattern;
* a per cent sign in the input is escaped and matches a literal per cent;
* an underscore in the input is escaped and matches a literal underscore;
* a backslash in the input is escaped — the case that fails if the three
  replacements run in the wrong order;
* input is lower-cased with `Locale.ROOT`.

### `GameQueryTest`

Domain validation, without Spring:

* `colour` without `playerId` is rejected;
* `from` after `to` is rejected;
* `from` equal to `to` is accepted — the bounds are inclusive;
* a null `sort` or `direction` is rejected rather than defaulted — defaults belong
  to `GameListParams`;
* a query with no filters at all is valid.

### `GameListParamsTest`

Boundary normalization and defaults, without a database:

* surrounding whitespace is removed from `event`;
* a blank `event` becomes null (no filter);
* omitted paging and sorting values receive the documented defaults.

### `GameSearchIT`

Testcontainers PostgreSQL, exercising the real Criteria query against the real
schema. This carries most of the weight.

Filtering:

* `playerId` alone returns games where that player had either colour;
* `playerId` with `colour` returns only that colour;
* `result` filters to one result;
* `from` and `to` are inclusive at both bounds;
* **an undated game is excluded by any date bound** — decision 7, asserted rather
  than assumed;
* `event` matches a substring case-insensitively;
* `event` does not match a game whose event is null;
* an event containing a literal per cent matches literally, and one containing an
  underscore does not match an arbitrary single character — the assertions that
  prove decision 5's escaping rather than arguing it;
* filters combine with `AND`.

Ordering and paging:

* an undated game sorts **last** under `DESC`;
* an undated game sorts **last** under `ASC` too — decision 6's deliberate choice,
  and the one that would silently regress if the null precedence were dropped;
* two games sharing a `played_on` keep a deterministic order across a page boundary:
  neither repeated nor skipped;
* `totalElements` counts the filtered set, not the returned page;
* a page past the end returns empty content with `totalElements` intact.

### `GameApiIT`

Added to the existing class, which shares one container across the class with no
cleanup between methods. That already forces each test to name its own players; a
list endpoint sharpens it, because an unfiltered query sees every game every other
test has ever created. **Every list test scopes itself with a filter that only its
own fixtures match** — its own player id, or a unique event string.

* a request with no parameters returns 200 and the envelope, with the documented
  defaults applied;
* a list row carries no `movetext` key — decision 3 on the wire;
* filters reach the query: a request scoped to one fixture's player returns exactly
  that fixture's games;
* `sort=movetext` returns 400 problem+json — decision 2 on the wire;
* `colour=WHITE` without `playerId` returns 400;
* `from` after `to` returns 400;
* `size=500`, `size=0` and `page=-1` each return 400;
* an `event` longer than 255 characters returns 400;
* a malformed `playerId` returns 400;
* an unknown `result` value returns 400;
* a filter matching nothing returns 200 with empty content and `totalElements: 0`.

### Not tested

No assertion on generated SQL text, and no assertion that the planner chooses an
index. Both are brittle against a Hibernate or PostgreSQL upgrade, and neither tests
behaviour a user can observe. The null-ordering assertions above cover what decision
6 is actually for.

No `@WebMvcTest` slice, for the reason #7 gave: it would assert against a mock what
the integration test already asserts against the real thing.

## Configuration and schema changes

None. No new dependencies — `spring-boot-starter-data-jpa` already brings the
Criteria API. No migration: every filtered column exists, and every index this
endpoint can use was created by V2.

`event` deliberately stays unindexed. The V2 comment said the right index depends on
whether the filter turned out to be exact or prefix; decision 5 makes it neither, so
the eventual answer is a `pg_trgm` GIN index. That is a migration plus an extension,
and it waits until a real dataset makes a sequential scan hurt.

## Risks

**The contract is inherited.** #10 builds its filtering controls against these
parameter names and this envelope, and #21 extends the same whitelist. Changing the
shape after #10 consumes it is expensive, which is why pagination model, row shape
and filter semantics were each settled as decisions rather than as implementation
details.

**The player filter is not usable from the UI yet.** Decision 4 takes a UUID, and
nothing today turns a name into one. #10 must either ship a player pick-list backed
by a player-listing endpoint or ship without the filter until #21.

**No authentication.** Any caller can list every game in the database until
[#25](https://github.com/guyAOgreen/Chess-App/issues/25). Games have no owner
column, so there is nothing to scope a query by even if there were a caller
identity. Read access is as open as the write access #7 already noted, and the same
condition applies: this must not reach a publicly reachable environment ahead of
authentication.

## Known limitations

**Offset paging can shift rows under a browsing user.** A game imported while
someone is on page 2 pushes a row from page 2 onto page 3, where they may see it
twice or not at all. The tie-break in decision 6 makes ordering *deterministic*; it
does not make offsets *stable*, and nothing offset-based can. Keyset pagination is
the fix, and decision 1 records why it is not worth its cost yet.

**`COUNT` runs on every request.** At a few hundred games it is free. It is the
first thing to make optional — a `count=false` parameter, or an estimate — if a
reference-game dataset ever arrives behind this endpoint.

**Rows and count do not share a database snapshot.** PostgreSQL's default
`READ COMMITTED` isolation gives each statement its own snapshot, so a concurrent
insert or delete can make `content` and `totalElements` momentarily disagree. This
is acceptable for the same small, low-write dataset that justifies offset paging.
If it becomes observable, run both reads in a repeatable-read, read-only
transaction or change the pagination contract.

**An ascending sort cannot use the index.** Decision 6, accepted deliberately. It
becomes a real cost only at a row count this endpoint is not built for.

**`event` filtering always scans.** No index can serve a leading-wildcard `LIKE`.
Same threshold, same answer: a trigram index when a dataset justifies the extension.

**Case folding happens in two collations.** Java lower-cases the input, PostgreSQL
lower-cases the column. They can disagree on non-ASCII input. Not reachable with the
event names this database will hold, and cheap to close later by folding only in the
database.

## Out of scope

* **`GET /api/games/{id}`** — [#9](https://github.com/guyAOgreen/Chess-App/issues/9),
  which keeps the full `GameResponse`.
* **Player search and name-based filtering** —
  [#21](https://github.com/guyAOgreen/Chess-App/issues/21), which also introduces the
  `ListGames` application class decision 9 defers.
* **Whether read paths need application-layer use cases on principle** — decision 9
  accepts the inconsistency for this endpoint and records why. Settling it for #9,
  #21 and every later read endpoint needs its own issue.
* **Sorting by anything but `played_on`** — one enum constant when a UI asks.
* **Filtering by `source`, `site`, `round`, `eco` or rating** — not in the issue, and
  each is one predicate when something needs it.
* **The frontend games list** — [#10](https://github.com/guyAOgreen/Chess-App/issues/10).
* **OpenAPI and generated frontend types** —
  [#27](https://github.com/guyAOgreen/Chess-App/issues/27).
* **Authentication and per-user scoping** —
  [#25](https://github.com/guyAOgreen/Chess-App/issues/25).
* **A trigram index for `event`** — waits for a dataset that needs it.
