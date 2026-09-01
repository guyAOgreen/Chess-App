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

**It parses with `loadPgn`, not by tokenising SAN itself.** Stored movetext is not
a plain list of moves. `PgnMoveCounter` on the backend shows what it may legally
contain: brace comments, `;` comments running to end of line, recursive
variations, NAGs, suffix annotations like `!?`, and move numbers attached to their
move (`1.e4`, `2...Nc6`). Splitting on whitespace and stripping digits would
mangle every one of those.

Reproducing that stripping on the frontend would mean duplicating
`PgnMoveCounter`'s logic — including the detail its javadoc flags, that a `;`
comment ends at its line, "which is what the specification says and precisely
where chesslib disagrees". Duplicating tricky chess-text parsing across two
languages is what CLAUDE.md warns against, and there is no need: `loadPgn` already
does it. So `replay` loads the movetext and reads the resulting history, and the
only text handling we own is none.

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

`react-router@8`, MIT, whose peer requirement (`react >= 19.2.7`) the repository
already satisfies.

#10 refused a router when there was one screen, on the grounds that "a router
earns its place when there is a second URL to route to". There is now. The routes
are `/` for the list and `/games/:id` for the viewer, and `GameRow` becomes a
`<Link>` — the one-file change #10 designed for by extracting the row as its own
component.

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

`MoveList` follows the same rule — plies, a current index, and a callback. #17
attaches recognition confidence to its plies without changing this contract.

No `orientation` prop, because decision 6 dropped flipping and nothing would set
it. Reversing the square array is a small change when something wants it.

### 8. A 404 is its own state, not a generic failure

`GET /api/games/{id}` answers 404 for an identifier that parses but matches no
game — #9 chose that deliberately, and chose 400 for one that does not parse at
all, so that "the request was malformed" and "the game is not here" stay
distinguishable.

The viewer preserves that distinction. "No game with that identifier", with a link
back to the list, is a different sentence from "the request failed", and only one
of them is worth offering a Retry for. Collapsing them would discard information
the backend went to trouble to provide.

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
other direction is no less plausible, and decision 2 names a specific candidate —
the two disagree about where a `;` comment ends.

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
│   └── App.tsx                        routes; shell around both pages
├── features/games/
│   ├── replay.ts                      PURE: movetext → Replayed. Only chess.js importer
│   ├── api/games.ts                   + gamePath(id), fetchGame(path, signal)
│   ├── types/game.ts                  + Game, Ply
│   ├── hooks/
│   │   ├── useGame.ts                 one game's request state, incl. 404
│   │   └── useReplay.ts               plies, current index, select
│   ├── components/
│   │   ├── Chessboard.tsx             + .module.css
│   │   ├── MoveList.tsx               + .module.css
│   │   ├── GameHeader.tsx             + .module.css
│   │   └── GameRow.tsx                becomes a <Link>
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
        │  loading / ready / not-found / failed│
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
backend is unreachable. It adds one arm for the 404 (decision 8).

`useReplay` holds only the current index and derives everything else. It takes
`plies` rather than movetext, so it never touches chess.js either.

## Rendering detail

**The board.** The FEN's placement field expands to 64 squares: `/` separates
ranks, a digit is that many empty squares, a letter is a piece. Squares are an 8×8
CSS grid. Two new custom properties in `index.css` carry the light and dark square
colours, defined in both colour schemes like every other token — the board is the
first thing in this application needing colours the current palette does not have.
Files a–h and ranks 1–8 label the edge squares.

Each piece is an `<img>` with an `alt` naming it ("white knight"), so the position
is not purely visual. A square with no piece has no image and no alt text — 64
announcements of "empty" would be worse than none.

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
* **the annotations movetext may legally carry** — a brace comment, a `;` comment,
  a NAG, a suffix annotation (`e4!?`), a recursive variation, and a move number
  attached to its move (`1.e4`, `2...Nc6`). Each must replay to the same positions
  as the same game written plainly. These are the cases decision 2 chose `loadPgn`
  for, so they are the tests that would fail if someone "simplified" it to a split
  on whitespace;
* empty movetext yields exactly the initial position;
* unparseable movetext returns `plies` holding only the initial position **and** an
  error, and does not throw;
* `plies[0]` is always the initial position with `san: null`.

### `Chessboard.test.tsx`

The starting FEN places a white rook on a1 and a black king on e8; the empty-board
FEN renders 64 squares and no pieces; each piece image has an alt text naming it.

### `MoveList.test.tsx`

Renders pairs by move number; marks the current ply with `aria-current`; clicking
a ply raises its index; the initial-position entry is selectable.

### `useGame.test.ts`

Loading then ready; a superseded request never lands (the abort discipline #10
established); a 404 produces `not-found` rather than `failed`; a transport failure
produces `failed`; `retry` re-requests.

### `useReplay.test.ts`

Starts at the initial position; `select` moves the index; an out-of-range index is
refused rather than producing an undefined FEN.

### `GameViewerPage.test.tsx`

One integration test through the real hooks with `fetch` stubbed: load a game,
click the sixth ply, assert the board shows that position. Plus the not-found and
failure states.

### `App.test.tsx`

A row in the list links to `/games/:id`, and that route renders the viewer.

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

**The two libraries disagree about `;` comments.** `PgnMoveCounter`'s javadoc
records that a `;` comment ends at its line "which is what the specification says
and precisely where chesslib disagrees" — so the backend hand-rolls that rule
rather than trusting its own library. chess.js is a third implementation of the
same rule. A game whose movetext carries a `;` comment is therefore the most likely
candidate for a genuine disagreement, and has a named test.

**The movetext contract.** `CanonicalPgn` appends the result token *separately*
from the movetext, so what the API returns is moves only, with no `1-0` to strip.
`replay` should not assume a terminal token can never appear, since a future
importer could change that, but it should not strip one speculatively either.

**Vendored assets are unversioned.** Twelve SVGs enter the repository with no
build step verifying they are what the licence file claims. The `LICENCE` file
naming author, licence and source is the whole mitigation.

**No authentication.** [#25](https://github.com/guyAOgreen/Chess-App/issues/25).
Any caller can view any game, because the endpoint permits it.

## Known limitations

**No arrow keys and no board flip.** Decision 6, deliberate. Both are small
follow-ups.

**Variations, comments and NAGs are stored but not shown.** Movetext may contain
all three — `PgnMoveCounter` exists precisely because it may — and the viewer
renders only the main line, because that is what `loadPgn` yields as history.
A game imported with annotations displays its moves and silently drops the
commentary. Nothing is lost from storage; it is simply not surfaced. Showing it
means designing how a variation reads in a move list, which is a feature, not an
oversight to correct here.

**The whole game is replayed on load.** Fine for a chess game — a long one is a
few hundred plies. It would not be fine for a database dump, which is not what
this is.

**Positions are recomputed on every mount.** Navigating away and back replays the
game. Imperceptible at this size, and caching it would need an invalidation story
for no current benefit.

**A row's link is the only way in.** There is no search-by-id and no URL sharing
affordance beyond copying the address bar.

## Out of scope

* **URL-synced list filters** — decision 5. Its own issue, unblocked by this one.
* **Arrow-key navigation and board flipping** — decision 6.
* **Engine analysis.** CONTEXT.md anticipates Stockfish eventually; nothing here
  assumes it.
* **Move annotations, variations, comments** — not stored.
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
