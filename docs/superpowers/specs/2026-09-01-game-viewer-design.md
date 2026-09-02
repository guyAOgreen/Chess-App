# Frontend: game viewer with board and move list

Date: 2026-09-01

Issue: [#11](https://github.com/guyAOgreen/Chess-App/issues/11) — M1, Game database

Chess terminology used here is defined in the [glossary](../../glossary.md).

## Goal

Replay a stored game: a chessboard, a move list, and navigation through the moves.

This completes the workflow CLAUDE.md names as the delivery priority — enter or
import a PGN, validate it, save it, view it. Import ([#7](2026-08-25-pgn-import-endpoint-design.md)),
validation ([#6](2026-08-24-pgn-parsing-design.md)), storage
([ADR 0002](../../adr/0002-game-storage.md)), the list endpoint
([#8](2026-08-26-game-list-endpoint-design.md)), the detail endpoint (#9) and the
list page ([#10](2026-08-28-games-list-page-design.md)) have all landed. "View
game" is the last step, and this is it.

It is also where two things #10 deliberately deferred come due. #10 shipped without
a router because there was one screen and nowhere for a table row to lead; its
decision 1 says a row's clickable affordance "stays a one-file change" and names
this issue as the change. And the issue asks for something #10 did not need:

> This viewer is later reused by the GameImport review screen, so keep it
> decoupled from how the game was loaded.

So the component boundaries here are a contract with
[#17](https://github.com/guyAOgreen/Chess-App/issues/17), not just an internal
tidiness preference.

## Decisions

### 1. The frontend replays the movetext, using chess.js

`GET /api/games/{id}` returns SAN movetext and no positions. Something has to turn
`1. e4 e5 2. Nf3` into a board, and this decision says which side does it.

It is already recorded. [ADR 0002](../../adr/0002-game-storage.md)'s rules for
derived data say M1 persists none, and that "the game viewer re-parses `movetext`
on read"; its table lists per-ply FENs as *computed on read*. The #6 spec repeats
it when declining to expose FEN, and `GameResponse`'s javadoc gives it as the
reason the detail response ships no assembled PGN. This spec confirms rather than
reopens that, having now costed it.

The cost is a second chess implementation in the repository —
`chess.js@1.4.0`, BSD-2-Clause — alongside chesslib on the backend. Two things
make that acceptable:

* **The frontend never adjudicates.** It replays movetext the backend already
  validated at import. It answers "what did the board look like after move 12",
  not "is this move legal". CLAUDE.md's rule is that the backend is authoritative
  where duplicated logic "determines canonical chess state"; nothing here does.
* **The alternative costs a round trip per game and a backend change this issue
  did not plan for.** ADR 0002 permits a positions endpoint, and it stays
  available if the two libraries ever disagree — see decision 9, which is what
  such a disagreement would look like on screen.

Hand-rolling SAN replay was rejected outright. Disambiguation, castling, en
passant and promotion are precisely where hand-written chess code goes wrong, and
getting it wrong shows the user a board that is confidently incorrect.

### 2. `replay.ts` is the only file that imports chess.js

One pure function, at the feature root:

```ts
export function replay(movetext: string): Replayed
```

Nothing else in `apps/web` may import `chess.js`. This is ADR 0001's pattern
applied on the frontend — that ADR wraps chesslib behind interfaces the core owns,
so that no code outside the chess module imports `com.github.bhlangonijr.*`, and
so the fallback stays real. The same argument holds here for the same reasons.

The consequence worth having: the hardest part of this feature is a pure function.
Testing "the FEN after 12 plies of the Ruy Lopez" needs no React, no DOM, no
network and no mocking — just an input string and an expected string.

**It parses with `loadPgn`, not by tokenising SAN itself.** Canonical movetext is
clean SAN with move numbers, and it is clean because the backend *regenerates* it
rather than storing what was submitted: `ValidatedMoves.of` builds it from
`MoveList.toSanWithMoveNumbers()` after replaying every move against
`Board.legalMoves()`, and its own javadoc calls the field "normalised SAN with move
numbers, trimmed". Comments, variations, NAGs and suffix annotations exist in the
submitted document, which is why `PgnMoveCounter` strips them to count what was
*submitted* — they never reach storage.

It is still a grammar rather than a whitespace-delimited list: move numbers may sit
adjacent to their move and SAN carries disambiguation, captures, promotion,
castling and check markers. `replay` calls `loadPgn` in strict mode and maps the
verbose main-line history to `Ply` values using each move's `after` FEN. It does
not parse or replay individual SAN tokens itself.

Strict mode is the deliberate choice: the input is machine-generated canonical SAN,
so anything chess.js considers non-strict is a divergence between the two libraries
and should surface as decision 9's visible error rather than as a silently
mis-rendered board.

chesslib on the backend genuinely requires a terminal result token to parse a
movetext at all: `ChesslibPgnParser` documents appending `*` to a **local copy**
because "chesslib only parses a game's movetext during iteration when the source
text ends with one of the four PGN result tokens". chess.js has no such
requirement, and `replay` does not append one — doing so is actively harmful
rather than harmless, since movetext that already carries a result token then
parses as two terminators and is rejected outright.

### 3. A hand-rolled board, not `react-chessboard`

An 8×8 CSS grid that takes a FEN and renders it.

`react-chessboard` is the obvious alternative and it is a good library. Its
principal feature is drag-and-drop move input, which this viewer never uses: the
issue asks for navigation through moves, not for entering them, and #17 corrects
notation rather than dragging pieces. What is left after removing that is a grid
and twelve images, against a dependency with its own styling that would sit
outside the custom properties every other component in this app uses.

Roughly a hundred lines buys complete control of the board's appearance and one
fewer dependency. If a later issue needs move input, adopting the library then is
a contained change, because decision 7 keeps the board's contract narrow.

### 4. The pieces are the Cburnett set, taken under its BSD option

Twelve SVGs vendored into `apps/web/public/pieces/`.

The set on Wikimedia Commons (author: Cburnett) is multi-licensed, selectable at
the user's discretion, under GFDL 1.2+, CC-BY-SA 3.0, 3-clause BSD, and GPL v2+.
**We take the 3-clause BSD option.** It is permissive, imposes no copyleft on this
application, and its only obligation is retaining the copyright notice — so the
directory carries a `LICENCE` file naming the author, the licence chosen, and the
source, and that file is part of the change rather than an afterthought.

This is recorded as a decision because it is the first third-party asset in the
repository, and because CONTEXT.md is explicit that licensing must be understood
before third-party content is redistributed. Choosing GPL or GFDL here would have
consequences for the whole application; choosing BSD has none.

### 5. `react-router`, with `/` and `/games/:id`

`react-router@8`, MIT. Its peer requirement is `react >= 19.2.7` and the repository
is on `^19.2.8`, so it is already satisfied; the resolved version is pinned by
`yarn.lock` rather than left to a transitive upgrade.

#10 refused a router when there was one screen, on the grounds that "a router
earns its place when there is a second URL to route to". There is now. The routes
are `/` for the list and `/games/:id` for the viewer. `GameRow` remains a semantic
`<tr>` — a link cannot validly wrap or replace a table row. A final "View" column
contains a `<Link>` to the game, giving every row one explicit,
keyboard-accessible navigation target without duplicating a link in every cell.
`GameTable` gains the matching column heading.

The router uses browser history. Development is covered by Vite's SPA fallback;
any production static host must likewise rewrite unknown application paths such
as `/games/{id}` to `index.html` while leaving `/api/*` to the backend. Without
that deployment rule, in-app navigation works but refreshing or opening a shared
viewer URL returns the host's 404.

**URL-synced list filters are not part of this.** #10 recorded that filters do not
survive a reload or the back button, and expected this issue to pick it up. It is
a distinct user-facing capability with its own decisions — which parameters
appear, whether the page number belongs in the URL, what Clear does to history —
and folding it in would mix two unrelated user stories in one review. It gets its
own issue, which this change unblocks by introducing the router.

### 6. Navigation is clicking a move; no keyboard, no flip

The move list is the navigation. Clicking a ply shows that position. There are no
Previous/Next buttons, no arrow-key handling and no board flip.

The issue asks for "keyboard **or** click navigation", so this satisfies it, and
stepping is not lost — consecutive plies sit next to each other in the list.

Recorded honestly: arrow keys are about fifteen lines and stepping one ply at a
time is a viewer's core interaction, so a user replaying a long game will be
targeting small text repeatedly. That was raised and deliberately deferred. Board
flipping is likewise deferred, which is why decision 7 drops the orientation prop
rather than shipping one nothing sets.

### 7. `Chessboard` takes a FEN and nothing else

This is the decoupling the issue asks for, made concrete.

`Chessboard` does not import chess.js, does not know what a game is, and cannot
fetch. It receives a FEN string, reads its placement field — the part before the
first space — and renders 64 squares. A FEN is a string; a string cannot carry an
assumption about how it was obtained.

That matters for #17 specifically: a half-recognised scoresheet may not yet be a
legal game, and a component that insisted on a validated `Game` would be unusable
there. A component that takes a position renders whatever position it is given.

`MoveList` follows the same rule — plies, a current index, and a callback. It is
decoupled from loading and chess.js, but this spec does not claim that its exact
props already model #17's recognition confidence or correction controls. That
screen may extend the presentation contract or compose those controls around the
list while continuing to reuse the board and replay-independent ply model.

No `orientation` prop, because decision 6 dropped flipping and nothing would set
it. Reversing the square array is a small change when something wants it.

### 8. A 404 is its own state, not a generic failure

`GET /api/games/{id}` answers 404 for an identifier that parses but matches no
game — #9 chose that deliberately, and chose 400 for one that does not parse at
all, so that "the request was malformed" and "the game is not here" stay
distinguishable. `useGame` validates the route parameter as a UUID before making a
request and exposes `invalid-id` for a malformed value; retrying a URL that cannot
possibly succeed would be misleading.

One divergence to accept knowingly. #9's javadoc records that `UUID.fromString` is
lenient — it accepts non-canonical dash-separated forms and widens each group, so
`/api/games/1-1-1-1-1` parses and comes back 404 rather than 400, and #9 left that
alone deliberately. A strict client-side check is therefore *stricter* than the
server: `/games/1-1-1-1-1` shows `invalid-id` here where the backend would have
said "not found". Both answers tell the user the same actionable thing — that
identifier will not open a game — so the divergence costs nothing, but it should be
a deliberate choice rather than a surprise, and the client must not be relied on as
the definition of a valid identifier.

The viewer preserves those distinctions. `invalid-id` and `not-found` each show a
specific message and a link back to the list. A transport or unexpected server
failure shows Retry. If the server nevertheless returns 400 for a locally valid
UUID, it is an unexpected rejected request and remains `failed`.

### 9. An unreplayable game degrades rather than blanks

`replay` returns what it managed plus an error, rather than throwing:

```ts
interface Replayed {
  plies: Ply[];            // always includes the initial position
  error: string | null;    // set when the movetext could not be replayed
}
```

This should be unreachable. The backend validated the movetext at import with
chesslib, and `POST /api/games` refuses anything it cannot parse. But "unreachable"
and "blank screen" are different outcomes, and there is a named way for it to
happen: chess.js and chesslib are different implementations, and
[#37](https://github.com/guyAOgreen/Chess-App/issues/37) exists because chesslib's
reader already mishandles documents that are legal PGN. A disagreement in the
other direction is no less plausible.

**The degradation is all-or-nothing, not a partial replay.** `loadPgn` throws on a
parse failure and offers no partial mode, so there is no "replayed as far as ply
29" to salvage. Chasing one — replaying progressively truncated movetext until it
stops working — would be guesswork built on a rejected parse.

So an unreplayable game shows its header, the stored movetext as plain text, the
starting position, and what went wrong. The user sees what is stored and why it
could not be drawn, which is the most useful possible bug report. The page never
renders nothing, and `plies` is never empty because `plies[0]` is the initial
position regardless.

### 10. Positions are computed once per game

`replay` runs in a `useMemo` keyed on the movetext, and every ply's FEN is held
from then on.

A 40-move game is 80 short strings. Recomputing per navigation would be wasteful
and would make clicking through the game visibly slower on a long one; storing
them costs a few kilobytes. There is nothing here to stream, paginate or
virtualise, and any structure that implied otherwise would be inventing a problem.

## Module layout

```text
apps/web/src/
├── app/
│   ├── App.tsx                        routes; shell around both pages
│   └── HomePage.tsx                   the list plus backend health, routed at /
├── features/games/
│   ├── replay.ts                      PURE: movetext → Replayed. Only chess.js importer
│   ├── format.ts                      + spokenResultLabel, viewLinkLabel
│   ├── api/games.ts                   + gamePath(id), fetchGame(path, signal)
│   ├── types/
│   │   ├── game.ts                    + Game
│   │   └── ply.ts                     + Ply
│   ├── hooks/
│   │   ├── useGame.ts                 one game's request state, incl. invalid ID and 404
│   │   └── useReplay.ts               plies, current index, select
│   ├── components/
│   │   ├── Chessboard.tsx             + .module.css
│   │   ├── squares.ts                 PURE: FEN placement → 64 squares
│   │   ├── MoveList.tsx               + .module.css
│   │   ├── GameHeader.tsx             + .module.css
│   │   └── GameRow.tsx                + explicit viewer <Link> cell
│   └── pages/
│       └── GameViewerPage.tsx         + .module.css
├── index.css                          + two board colour tokens
└── public/pieces/                     12 SVGs + LICENCE
```

`replay.ts` sits at the feature root beside `format.ts`, for the same reason:
pure, used by more than one thing, not a component.

## Component contracts

```ts
Chessboard  { fen: string }
MoveList    { plies: Ply[]; current: number; onSelect: (index: number) => void }
GameHeader  { game: Game }
```

```ts
interface Ply {
  index: number;         // 0 is the initial position
  moveNumber: number;    // 1-based; 0 for the initial position
  colour: 'white' | 'black' | null;   // null for the initial position
  san: string | null;                 // null for the initial position
  fen: string;
}
```

The initial position is a `Ply` rather than a special case. Without it, `current`
has no honest value before the first move, and every consumer needs an
"is anything selected yet" branch. With it, `plies[0]` is the starting position and
`current` is always a valid index.

## Data flow

```text
  route /games/:id
        │ id
        ▼
    useGame ─────▶ GET /api/games/{id} ─────▶ Game { …, movetext }
        │                                      │
        │  loading / ready / invalid-id / not-found / failed
        ▼                                      ▼
   GameViewerPage                      replay(movetext)   ← pure, chess.js, useMemo
        │                                      │
        │                                 Replayed { plies, error }
        ▼                                      │
    useReplay ◀────────────────────────────────┘
        │  { plies, current, select }
        ├──▶ GameHeader   game
        ├──▶ Chessboard   fen={plies[current].fen}
        └──▶ MoveList     plies, current, onSelect={select}
```

`useGame` reuses the request-state shape #10 settled — a discriminated union with
an `AbortController` and a `retry` — so both pages behave identically when the
backend is unreachable. It adds arms for an invalid route identifier and a 404
(decision 8).

`useReplay` holds only the current index and derives everything else. It takes
`plies` rather than movetext, so it never touches chess.js either. When the route
changes to another successfully loaded game, the ready viewer is keyed by game ID
so selection resets to the initial position; a stale index from the previous game
must never select or index past the new game's plies.

## Rendering detail

**The board.** The FEN's placement field expands to 64 squares: `/` separates
ranks, a digit is that many empty squares, a letter is a piece. Squares are an 8×8
CSS grid. Two new custom properties in `index.css` carry the light and dark square
colours, defined in both colour schemes like every other token — the board is the
first thing in this application needing colours the current palette does not have.
Files a–h and ranks 1–8 label the edge squares.

The board has an accessible name that identifies it as the current chess
position. Each occupied square exposes a label containing both coordinate and
piece (for example, "e4, white pawn"), rather than repetitions of "white pawn"
with no location. Empty squares are not individually announced. Coordinate labels
remain visible and are not the accessible name's only source.

**The move list.** Rows of move number, White's move, Black's move — the shape a
scoresheet has, which is not incidental: #17 puts a scoresheet image beside this
component. Each ply is a `<button>`; the current one carries `aria-current="true"`
so the selection reaches a screen reader rather than being conveyed by colour
alone.

**The header.** Both players with their game-time ratings, event, site, round,
date, result and ECO, using `format.ts`'s existing `sideLabel`, `resultLabel`,
`sourceLabel` and `orDash` so a game reads the same way here as it does in the
list.

## Page states

| State | What shows |
|---|---|
| `loading` | "Loading game…" |
| `ready` | Header, board, move list |
| `invalid-id` | "That game identifier is invalid." and a link to the list |
| `not-found` | "No game with that identifier." and a link to the list |
| `failed` | The failure message and Retry |
| `ready`, replay failed | Header, the stored movetext as plain text, the starting position, and the error |

## Testing

Vitest and React Testing Library, matching the suite #10 established. `fetch` is
stubbed; no test touches the network.

### `replay.test.ts`

Where the weight sits, and it needs no React at all:

* a known opening's FEN at a known ply, asserted exactly;
* castling both sides, en passant, and promotion — the moves that break hand-rolled
  replay, kept as tests even though a library does the work, because they are what
  a library swap would have to survive;
* SAN disambiguation (`Nbd7`);
* canonical SAN formatting as `MoveList.toSanWithMoveNumbers()` actually emits it —
  establish the real shape from a stored game rather than assuming, and pin it, so
  that a chesslib upgrade changing the formatting fails here rather than in the
  browser;
* movetext with no terminal token, which is what the API returns, parses
  without error;
* movetext that does carry a trailing result token still parses, guarding
  against ever reintroducing a locally-appended terminator that would reject it;
* empty movetext yields exactly the initial position;
* unparseable movetext returns `plies` holding only the initial position **and** an
  error, and does not throw;
* `plies[0]` is always the initial position with `san: null`.

### `Chessboard.test.tsx`

The starting FEN places a white rook on a1 and a black king on e8; the empty-board
FEN renders 64 squares and no pieces; an occupied square's accessible label names
both its coordinate and piece; malformed FEN fails predictably rather than
silently drawing a shifted or incomplete board.

### `MoveList.test.tsx`

Renders pairs by move number; marks the current ply with `aria-current`; clicking
a ply raises its index; the initial-position entry is selectable.

### `useGame.test.ts`

Invalid UUID makes no request; loading then ready; a superseded request never lands
(the abort discipline #10 established); a 404 produces `not-found` rather than
`failed`; a transport failure produces `failed`; `retry` re-requests.

### `useReplay.test.ts`

Starts at the initial position; `select` moves the index; an out-of-range index is
refused rather than producing an undefined FEN; loading a different game resets
selection to the initial position.

### `GameViewerPage.test.tsx`

One integration test through the real hooks with `fetch` stubbed: load a game,
click the sixth ply, assert the board shows that position. Plus the not-found and
failure states.

### `App.test.tsx`

A row in the list links to `/games/:id`, and that route renders the viewer.

### An existing test this change breaks

#10's `GameTable.test.tsx` asserts nine column headers, in order, each carrying
`scope="col"` — a test added precisely because swapping two headers had previously
survived the suite. Decision 5 adds a tenth column, so that assertion must be
updated rather than deleted, and the View column needs the same `scope="col"`
treatment as the other nine.

### Not tested

The piece SVGs and the board's colours. Asserting a class name or an image path is
a test of the implementation.

## Risks

**Two chess implementations can disagree.** Decision 1 accepts this and decision 9
makes a disagreement visible rather than silent. #37 already records that
chesslib's reader mishandles some legal PGN, so the risk is real in both
directions. The mitigation if it becomes a pattern is the positions endpoint ADR
0002 permits.

**`chess.js` throws rather than returning null.** Both `move()` ("throws an
'Illegal move' exception if the move was illegal") and `loadPgn` ("will throw an
exception if the PGN fails to parse") raise on failure. So `replay` must catch, and
a `replay` that let the exception escape would blank the page — exactly what
decision 9 exists to prevent. This is a test, not a comment.

**The movetext contract.** `CanonicalPgn` appends the result token *separately*
from the movetext, so what the API returns is moves only, with no `1-0` to strip.
chesslib on the backend genuinely requires a terminal result token to parse a
movetext at all — `ChesslibPgnParser` appends `*` to a local copy for exactly
that reason. chess.js has no such requirement, and `replay` does not append one:
doing so would be actively harmful rather than harmless, since movetext that
already carries a result token would then parse as two terminators and be
rejected outright.

**Vendored assets are unversioned.** Twelve SVGs enter the repository with no
build step verifying they are what the licence file claims. The `LICENCE` file
naming author, licence and source is the whole mitigation.

**No authentication.** [#25](https://github.com/guyAOgreen/Chess-App/issues/25).
Any caller can view any game, because the endpoint permits it.

## Known limitations

**No arrow keys and no board flip.** Decision 6, deliberate. Both are small
follow-ups.

**Variations, comments and NAGs are not shown.** The import parser uses them while
validating submitted PGN but, as ADR 0002 specifies, removes them from canonical
`movetext`; the original annotated document remains available only as provenance
in `sourcePgn`. Displaying annotations requires first modelling an annotated game
representation rather than asking the viewer to infer one from canonical moves.

**The whole game is replayed on load.** Fine for a chess game — a long one is a
few hundred plies. It would not be fine for a database dump, which is not what
this is.

**Positions are recomputed on every mount.** Navigating away and back replays the
game. Imperceptible at this size, and caching it would need an invalidation story
for no current benefit.

**A row's link is the only way in.** There is no search-by-id and no URL sharing
affordance beyond copying the address bar.

**`replay`'s FEN output is not byte-identical to chesslib's.** chess.js emits
the en-passant target square only when the capture is legal, while the standard
FEN convention chesslib follows records it after every double pawn push. Inert
today because only the placement field is ever read. It would matter if
position identity were ever computed on both sides of the wire —
CONTEXT.md's position indexing for opponent preparation is the case to watch.

**The board is a flat list of 32 labelled cells, not a navigable grid.**
Because every label carries its coordinate, the position is fully recoverable —
but only by listening to all thirty-two announcements in reading order. There
is no way to ask what is on a given square, no keyboard navigation, and empty
squares are silent, so a listener cannot distinguish an empty square from one
they skipped. This is a deliberate limit for a read-only viewer. `role="grid"`
with a roving tabindex and labels on empty squares is the upgrade path —
worth noting that #17 will hand this component half-recognised positions,
where inspecting one specific square matters most.

## Out of scope

* **URL-synced list filters** — decision 5. Its own issue, unblocked by this one.
* **Arrow-key navigation and board flipping** — decision 6.
* **Engine analysis.** CONTEXT.md anticipates Stockfish eventually; nothing here
  assumes it.
* **Move annotations, variations, comments** — not stored in canonical
  `movetext`; the submitted source document is retained as provenance.
* **PGN export.** `GameResponse` deliberately carries neither `sourcePgn` nor an
  assembled document; export is a distinct representation, decided when something
  needs it.
* **Editing or entering moves** — the viewer is read-only. #17 owns correction.
* **Opening names.** The ECO tag is displayed as stored; ADR 0001 left
  classification undecided.
* **The scoresheet review screen** — [#17](https://github.com/guyAOgreen/Chess-App/issues/17),
  which reuses `Chessboard` and `MoveList` through the contracts in decision 7.
* **Generated API types** — [#27](https://github.com/guyAOgreen/Chess-App/issues/27),
  which replaces the `Game` type added here along with the rest.
* **Authentication** — [#25](https://github.com/guyAOgreen/Chess-App/issues/25).
