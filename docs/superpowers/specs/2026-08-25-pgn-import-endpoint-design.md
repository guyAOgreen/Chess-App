# POST /games — PGN import endpoint

Date: 2026-08-25

Issue: [#7](https://github.com/guyAOgreen/Chess-App/issues/7) — M1, Game database

Chess terminology used here is defined in the [glossary](../../glossary.md).

## Goal

Accept a PGN document over HTTP, validate it deterministically, resolve its
players, persist the game, and return the created resource.

This is the endpoint the whole of M1 was built towards. The chess work is already
done: [#6](2026-08-24-pgn-parsing-design.md) produces validated facts or a clear
rejection, [#4](2026-08-22-player-domain-design.md) resolves a PGN name to a
`Player`, and [#5](https://github.com/guyAOgreen/Chess-App/issues/5) with
[ADR 0002](../../adr/0002-game-storage.md) stores the result. What remains
is orchestration and an HTTP contract.

The contract matters more than the orchestration. This is the first HTTP surface
in the core service — there is no `api` package, no controller, no DTO and no
error-body convention. Whatever is chosen here is inherited by
[#8](https://github.com/guyAOgreen/Chess-App/issues/8),
[#9](https://github.com/guyAOgreen/Chess-App/issues/9),
[#14](https://github.com/guyAOgreen/Chess-App/issues/14) and
[#18](https://github.com/guyAOgreen/Chess-App/issues/18), and codified by
[#27](https://github.com/guyAOgreen/Chess-App/issues/27).

## Decisions

### 1. A rejection is a return value all the way to the controller

`ImportPgn.execute` returns a sealed `PgnImportResult` of `Imported(Game)` or
`Rejected(PgnError)`. The controller pattern-matches on it.

This carries #6's reasoning one layer outward. That design chose a sealed
`PgnParseResult` because "for an import endpoint fed by users, an invalid document
is an expected outcome rather than an exceptional condition, and a sealed type
makes the failure impossible to forget at the call site". The call site in question
is this controller. Re-wrapping the rejection as an exception at the application
boundary would discard the type the parsing module deliberately built, and move the
most important part of this endpoint's contract — what an invalid PGN looks like —
into an advice class that has nothing else to do with games.

The controller stays thin in the sense `CLAUDE.md` requires: it switches on the
result, maps each arm to a response, and does no business logic. Deciding that
`ILLEGAL_MOVE` is a 422 is an HTTP concern, and HTTP concerns are what the API
layer is for.

### 2. RFC 9457 problem details, with `code` and `ply` as extension members

Errors are `application/problem+json`. The standard members carry the human-facing
part and two extension members carry the machine-facing part:

```json
{
  "type": "/errors/invalid-pgn",
  "title": "Invalid PGN",
  "status": 422,
  "detail": "move 3. Nf3 is not legal in this position",
  "code": "ILLEGAL_MOVE",
  "ply": 5
}
```

`type` distinguishes the class of error; `code` distinguishes which of the ten
`PgnErrorCode` values occurred. Clients switch on `code`. `ply` is the parser's
1-based half-move index, and is omitted rather than null when the problem is not
about a specific move — the game viewer (#11) will use it to point at the offending
move, and an absent field is easier to branch on than a null one.

A custom error envelope was considered and rejected: ProblemDetail is a standard,
Spring builds it for us, and framework-level errors already use it.

### 3. `spring.mvc.problemdetails.enabled` must be turned on

It defaults to `false` in Spring Boot 4.1 (confirmed against
`spring-boot-webmvc-4.1.0.jar`'s configuration metadata). Left at the default,
malformed JSON, an unsupported method and a wrong content type return the legacy
`{"timestamp","error","path"}` body while our own rejections return problem+json.

Two error formats on one API is worse than either, and a client written against
one of them will mis-handle the other. Enabling it is a one-line change to
`application.yml` and is part of this issue rather than a later tidy-up.

### 4. 422 for an unusable game, 400 for an unusable request

A well-formed JSON body carrying a PGN that cannot become a `Game` is
422 Unprocessable Content: the request was understood, and the content was the
problem. A body that is not readable JSON at all, or a `pgn` field beyond the size
cap, is 400.

The distinction is not pedantry. It tells a client whether to show the user a chess
problem it can point at in their document, or a transport problem it should log.

All ten `PgnErrorCode` values map to 422. There is no attempt to give `NOT_PGN` a
different status from `ILLEGAL_MOVE`: both mean "this document cannot be stored as
a game", the `code` says which, and splitting the status would give clients two
things to branch on instead of one.

### 5. A missing `pgn` field is answered by the parser, not by bean validation

`{}` deserialises to `ImportPgnRequest(null)`, and `PgnParser.parse(null)` already
answers `NOT_PGN` with "no PGN text was supplied". Adding `@NotBlank` would produce
a second code path reaching the same conclusion in a different format with a
different status.

The size cap is different, and does use bean validation — see decision 9.

### 6. `ImportPgn` declares no transaction

Both repository adapters already manage their own boundaries, and one of them
manages it in a way an outer transaction cannot change.
`PlayerRepositoryAdapter.createOrFind` is `REQUIRES_NEW` deliberately: its javadoc
records that resolving a player commits independently of the caller, so a
subsequently failed import leaves the player row behind, and that this is intended
because a `Player` is shared reference data rather than part of any one game.
`GameRepositoryAdapter.save` is a single insert, atomic on its own.

An `@Transactional` on `execute` would therefore wrap nothing the inner boundaries
do not already cover, while implying an atomicity that `REQUIRES_NEW` explicitly
breaks. A reader would reasonably infer that a failed insert rolls the players
back. It does not. Better no boundary and a comment saying why than a decorative
one that misleads.

This is worth revisiting if the import ever writes more than one row of its own —
duplicate detection, for instance, would change the answer.

### 7. `PlayerIdentityConflict` is not mapped

`createOrFind` throws it in exactly one circumstance: a **new** display name is
being inserted carrying a FIDE ID that already belongs to a different, existing
player. `ParsedGame` carries no FIDE ID, so PGN import always calls
`execute(name, null, null)` and the condition cannot arise.

Mapping it to 409 would be a handler no request can reach, and an untestable one.
It is added when an endpoint actually supplies identity data — manual player
creation, or a provider import that carries FIDE identifiers.

### 8. The player name stored on the game is the document's, not the resolved player's

`GameSide.name` takes `ParsedGame.whiteName()`, not `Player.displayName()`.

They are the same string today, because matching is exact on the trimmed name and
both types trim. But they mean different things: `GameSide.name` is a game-time
snapshot, which is the entire reason ADR 0002 gave it its own column — renaming or
merging a `Player` must not rewrite historical exports. Taking it from the resolved
player would make the two coincide by accident and would silently start rewriting
history the day aliasing (#21) makes matching non-exact.

### 9. An application-level size cap on the submitted document

`pgn` is capped at 1,048,576 UTF-16 code units with `@Size`, producing a 400.
This is a character-count limit, not a byte limit: Bean Validation measures the
deserialised `String`, and Java strings are UTF-16. Calling it a 1 MB limit would
therefore promise something the implementation does not enforce.

This endpoint is unauthenticated until #25, and Spring Boot offers no property
that limits a JSON request body: `server.tomcat.max-http-form-post-size` applies
to form content only, and the generic `server.max-http-post-size` has been
deprecated at error level since 3.0. A heavily annotated 200-move game is tens of
kilobytes, so the cap is generous by more than an order of magnitude while still
bounding the work the parser can be asked to do.

`@Size` is deliberately described as an application-level limit. Jackson must
read and deserialise the request before validation runs, so this does **not** cap
bytes received over the network or protect the server from an arbitrarily large
HTTP body. A transport-level request limit belongs in deployment infrastructure
or a deliberately tested servlet filter. That broader denial-of-service control
is required before public deployment, but is not introduced by this endpoint.

This is beyond the issue text, and is included because it is a property of the
endpoint rather than a feature: adding it later means changing a published
contract.

### 10. The API is mounted at `/api`, declared literally

Controllers declare `@RequestMapping("/api/games")`.

The vite dev server proxies `/api` to the backend without rewriting the path, so
the backend must serve `/api/games`. Spring MVC has no base-path property —
`spring.mvc.servlet.path` moves the `DispatcherServlet` and would take the actuator
endpoints with it.

The alternative is a `WebMvcConfigurer` calling
`addPathPrefix("/api", forBasePackage("com.chessapp"))` once, leaving controllers
declaring `/games` as `CONTEXT.md` names the resources. It was rejected because a
reader searching the repository for `/api/games` would find nothing, and one string
literal per controller is cheaper than indirection every newcomer has to discover.

### 11. The response carries no assembled PGN

`GameResponse` returns canonical `movetext` and the public game metadata, not the
document `CanonicalPgn.from(Game)` would assemble and not `sourcePgn`.

The game viewer (#11) re-parses movetext to drive the board — the #6 spec records
that as the plan — so shipping the assembled document as well would send the same
moves twice in every response, including every row of the #8 list if that reuses
the type. Export as a PGN file is a real requirement, but it is a distinct
representation of an existing resource and belongs behind a content negotiation or
an `/api/games/{id}.pgn` route, decided when something needs it.

`sourcePgn` is provenance, not part of the resource representation. Returning it
would duplicate the moves, expose comments or other submitted data that the
canonical model does not use, and couple later read endpoints to an audit field.
It remains available internally for traceability. A future explicit source or
export use case can decide its own authorisation and representation.

## Package layout

```text
com.chessapp.game
├── api/
│   ├── GameController.java       POST /api/games
│   ├── ImportPgnRequest.java     record(String pgn)
│   └── GameResponse.java         record + nested Side; from(Game)
│
├── application/
│   ├── ImportPgn.java            orchestration
│   └── PgnImportResult.java      sealed: Imported | Rejected
│
├── domain/                       unchanged
└── persistence/                  unchanged
```

Nothing is added to `shared/`. Decision 7 removes the only candidate for a global
`@RestControllerAdvice`, and decision 1 keeps rejection handling in the controller,
so introducing the package now would be creating a home for nothing.

`GameResponse` is built from the domain `Game`. `GameEntity` does not leave
persistence, as `CLAUDE.md` requires.

## The endpoint contract

### Request

```http
POST /api/games
Content-Type: application/json

{ "pgn": "[Event \"Obs Club Championship\"]\n..." }
```

`pgn` is the only field. `source` is fixed server-side to `PGN_IMPORT`, which is
true by construction of anything arriving here; a client-declared provenance is a
field that would have to be defended and that nothing yet needs.

### Success

```http
HTTP/1.1 201 Created
Location: /api/games/0199f3c1-...
Content-Type: application/json

{
  "id": "0199f3c1-...",
  "white": { "playerId": "...", "name": "Green, Guy",      "rating": 1834 },
  "black": { "playerId": "...", "name": "Adams, Michael",  "rating": 2680 },
  "event": "Obs Club Championship",
  "site": null,
  "round": "3",
  "playedOn": "2026-03-14",
  "result": "BLACK_WON",
  "eco": "B90",
  "source": "PGN_IMPORT",
  "movetext": "1. e4 c5 2. Nf3 d6 ..."
}
```

`Location` is a relative URI built from the created identifier rather than from
`ServletUriComponentsBuilder`. There is no proxy configuration to make an absolute
URI correct behind, and a relative one cannot be wrong.

This representation is the game detail contract, and #9 returns the same shape for
`GET /api/games/{id}`. Null-valued optional metadata is present as `null` rather
than omitted, so a client sees one shape whatever the document said.

### Failure

| Case | Status | `code` |
| --- | --- | --- |
| Any `PgnErrorCode` | 422 | the code |
| `pgn` absent or null | 422 | `NOT_PGN` |
| `pgn` over the size cap | 400 | — |
| Body not readable as JSON | 400 | — |
| Empty request body | 400 | — |
| Wrong method | 405 | — |
| Wrong content type | 415 | — |
| Domain construction fails | 500 | — |

The 500 row is a bug, not an input case. It is listed because the analysis below
concludes it cannot be reached, and a future change that makes it reachable should
be recognisable as a regression rather than as new behaviour.

## Why the parser cannot produce a game the domain rejects

`ImportPgn` builds `NewGame` and `GameSide` from `ParsedGame` without
re-validating. That is only safe if every `ParsedGame` the parser can emit already
satisfies `GameValues`. It does:

* **ECO** — `PgnTagValues.eco` returns null unless the value matches
  `[A-E][0-9]{2}`, the same pattern `GameValues.eco` enforces.
* **Ratings** — `PgnTagValues.rating` returns null for anything non-numeric or not
  positive; `GameValues.rating` rejects only non-positive values.
* **Date** — `PgnTagValues.date` returns null unless the date is fully known and
  real; `playedOn` is nullable.
* **Player names** — `checkPlayers` rejects a name that is missing, blank, `?` or
  carries an ISO control character, which is exactly what `GameValues.playerName`
  rejects.
* **Optional tags** — `PgnTagValues.optional` nulls a value carrying an ISO control
  character.
* **Movetext** — generated from validated half-moves, so it carries no tag pairs
  and no terminal result token.

The one apparent gap is U+2028 and U+2029, which `GameValues` rejects and
`PgnTagValues` does not. It is not reachable, though not for the reason the tag
readers alone suggest. `PgnTagReader` splits the document on `\R`, which matches
both characters, so a tag value carrying one is spread over two lines and matches
the tag pattern on neither. But `PgnTagReader.movetext` keeps every line that is
not a tag pair, so the two orphaned fragments land in the movetext section, and
chesslib fails on them.

Verified empirically against `ChesslibPgnParser`: a separator in `Event` and a
separator in `White` are both rejected as `NOT_PGN` with "the text could not be
read as PGN". The document never reaches `ParsedGame`, so nothing carrying a
separator can reach domain construction.

An `IllegalArgumentException` escaping `ImportPgn` therefore indicates a defect in
this reasoning rather than bad input, and 500 is the correct answer for it. The
U+2028 case is asserted as a test, so the conclusion is checked rather than merely
argued — and it is worth checking, because the first version of this analysis had
the mechanism wrong while reaching the right conclusion.

## Orchestration

```text
ImportPgn.execute(String pgn)

  parser.parse(pgn)
      │
      ├── Rejected(error) ──────────────► PgnImportResult.Rejected(error)
      │                                   nothing has touched the database
      │
      └── Parsed(game)
             │
             ├── findOrCreatePlayer.execute(game.whiteName(), null, null)
             ├── findOrCreatePlayer.execute(game.blackName(), null, null)
             │
             ├── games.save(new NewGame(
             │        new GameSide(white.id(), game.whiteName(), game.whiteRating()),
             │        new GameSide(black.id(), game.blackName(), game.blackRating()),
             │        game.event(), game.site(), game.round(), game.playedOn(),
             │        game.result(), game.eco(),
             │        GameSource.PGN_IMPORT,
             │        game.movetext(),
             │        pgn))                  ◄── the deserialised PGN string unchanged
             │
             └──────────────────────────► PgnImportResult.Imported(game)
```

Parsing comes first so an invalid document never reaches the database, and so the
common failure costs no connection.

`sourcePgn` is the deserialised `pgn` value character-for-character, byte order
mark included. It is not the original HTTP byte sequence: JSON escapes and the
request character encoding have already been decoded before the controller sees
the value.
ADR 0002 makes it provenance that nothing reads to answer a product question, and
normalising it would defeat the point of keeping it.

A game whose two colours name the same player resolves both sides to one `Player`
row and stores it. That is a legal, if unusual, PGN and there is no reason to
reject it.

## Configuration changes

`services/core/src/main/resources/application.yml`:

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

No other configuration changes. No new dependencies: `spring-boot-starter-web` and
`spring-boot-starter-validation` are already present.

## Testing

### `GameApiIT`

`@SpringBootTest` with `@AutoConfigureMockMvc` and a Testcontainers PostgreSQL
instance, matching the existing IT pattern. It exercises the real controller, real
JSON serialisation, real error handling and the real database.

* a valid PGN returns 201, a `Location` header, and a body whose fields match the
  document — and the row is actually in `games`;
* both players are created, and a second import naming one of them reuses that row
  rather than creating a second;
* an illegal move returns 422 with `code: ILLEGAL_MOVE` and the `ply`;
* `NOT_PGN`, `PLAYER_UNKNOWN`, `MULTIPLE_GAMES`, `NO_MOVES` and `RESULT_MISSING`
  each return 422 with their own code;
* a rejection with no ply omits `ply` rather than sending null;
* every error response is `application/problem+json`;
* `{}` returns 422 `NOT_PGN`;
* a malformed body returns 400 in problem+json — the assertion that decision 3
  actually took effect;
* an empty request body returns 400 in problem+json;
* a `pgn` over the cap returns 400;
* a document whose `Event` tag contains U+2028 returns 422 `NOT_PGN` rather than
  500 — the check on the analysis above. The assertion is that it does not reach
  domain construction, not that it imports.

### `ImportPgnIT`

Orchestration directly, without HTTP:

* `sourcePgn` equals the deserialised `pgn` value character-for-character,
  including a byte order mark;
* `movetext` is the canonical form, not the submitted text;
* `source` is `PGN_IMPORT`;
* a rejected document leaves `games` and `players` untouched;
* a game naming the same player as both colours stores one player and one game.

### Not tested

No `@WebMvcTest` slice. It would need a mocked `ImportPgn` and would assert the
same controller behaviour the IT already asserts against the real one, which is
duplication rather than coverage. If context startup becomes slow enough to matter,
that is the point to reconsider.

## Risks

**The contract is inherited.** #8 and #9 adopt `GameResponse` and the problem
details convention. Getting the representation wrong is cheap to fix now and
expensive after #10 and #11 consume it. This is why the response shape is treated
as a decision in its own right rather than as an implementation detail.

**No authentication.** Any caller can create games until #25. The character cap
bounds parser work after deserialisation; it neither limits bytes received nor the
number of requests. This is accepted for local development and must not reach a
publicly reachable environment ahead of authentication and transport-level request
limits.

## Known limitations

**Duplicate imports create duplicate games.** There is no uniqueness constraint on
`games`, so pasting the same PGN twice stores it twice. Defining "the same game"
is a real design question — same movetext, or same players and date, and how
tolerant of differing `Event` tags — and enforcing it race-safely needs a migration.
It gets its own issue rather than riding along on the first endpoint.

**No delete.** A game imported by mistake cannot be removed through the API. With
duplicates possible, this will be wanted early.

**No transport-level request limit.** Decision 9 caps the deserialised `pgn`
value, which bounds parser work but not the bytes read off the wire: Bean
Validation runs after Jackson. Spring Boot offers no property that would close
this — `server.max-http-post-size` has been deprecated at error level since 3.0,
and `server.tomcat.max-http-form-post-size` applies to form content only — so it
needs a reverse proxy limit or a servlet filter. Tracked as a deployment
prerequisite alongside #25 rather than left to be rediscovered.

## Out of scope

* **`GET /api/games` and `GET /api/games/{id}`** — #8 and #9, which reuse
  `GameResponse`.
* **OpenAPI and generated frontend types** — #27.
* **Authentication and ownership** — #25. Games have no owner column yet.
* **PGN export** — a distinct representation, decided when something needs it.
* **Multi-game documents** — rejected by the parser; they need their own
  provenance model, per ADR 0002.
* **Frontend import UI** — #10 and #11.
