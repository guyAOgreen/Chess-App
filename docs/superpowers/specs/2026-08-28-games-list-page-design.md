# Frontend: games list page

Date: 2026-08-28

Issue: [#10](https://github.com/guyAOgreen/Chess-App/issues/10) — M1, Game database

Chess terminology used here is defined in the [glossary](../../glossary.md).

## Goal

Build `apps/web/src/features/games` — a page that lists stored games with filtering
controls and pagination, composed from a feature API module, feature hooks and
presentational components, with no business logic in the components.

This is the frontend's first real feature. What exists today is the scaffolding from
[#1](2026-08-18-scaffolding-design.md): one `system-health` feature in the
`api/ hooks/ components/` shape, React and React DOM as the only dependencies, and a
single global stylesheet inherited from the Vite template. There is no router, no
data-fetching library, no component styling and no list of anything.

So this issue settles more than a page. How a feature fetches, how a page holds
state, what a request failure looks like on screen, and where styles live are all
decided here and inherited by the game viewer
([#11](https://github.com/guyAOgreen/Chess-App/issues/11)), the scoresheet review
screen ([#17](https://github.com/guyAOgreen/Chess-App/issues/17)) and player search
([#21](https://github.com/guyAOgreen/Chess-App/issues/21)). The decisions below are
weighted accordingly: the page itself is a table and a form.

The endpoint is finished and its contract is fixed —
[#8](2026-08-26-game-list-endpoint-design.md) settled the parameter names, the page
envelope and the row shape precisely because this issue consumes them. Nothing here
asks the backend to change.

## Decisions

### 1. No router yet

`App` renders the games page directly. `react-router` is not added.

The issue delivers one screen. A router earns its place when there is a second URL
to route to, and the first is the game viewer in #11 — which needs `/games/:id`,
will introduce the dependency then, and will turn the table rows into links in the
same change. Adding it now means an app-shell restructure this issue does not need,
in service of a route that does not exist.

The visible consequence is that a row links nowhere. That is honest: there is
nothing to open. It is also the thing #11 changes first, so the table is written
with the row as its own component boundary and a row's clickable affordance stays a
one-file change.

A hand-rolled `location.hash` switch was rejected outright. It is a second-rate
router that #11 discards.

CLAUDE.md's rule is the one being followed: the simplest design that satisfies the
current requirement, and no future complexity without a current need.

### 2. The filter controls ship only what a user can operate

Controls for `result`, a `from`/`to` date range, and `event`. No `playerId`, no
`colour`.

The endpoint takes `playerId` as a UUID — decision 4 of #8, and the right call for
an API. But no endpoint turns a name into an identifier: `player` has a domain,
persistence and a `FindOrCreatePlayer` use case, and no controller at all. Player
search is #21.

That leaves three ways to treat the player filter, and only one is defensible:

* **A raw UUID text input.** Nobody knows a player's UUID. The control is furniture
  that cannot be used, and `colour` — which #8 refuses unless `playerId` is present
  — is furniture attached to furniture.
* **A dropdown built from the players on the current page.** Looks usable and lies.
  It offers only players already on screen, and its options change as the page
  changes, so the same filter means something different from one page to the next.
* **Ship without them.** The page offers three filters that all work.

`GamesQuery` still carries `playerId` and `colour` as optional fields, and the
query-string builder serialises whatever is set. #21 adds a control and a value; it
does not touch the API module or the hook. The cost of keeping the door open is two
optional fields in a type.

### 3. A hand-rolled `useGames` hook, not a query library

`useGames(query)` is `useEffect` + `AbortController` + a discriminated state union,
extending the `useBackendHealth` pattern already in the tree.

TanStack Query is the obvious alternative, and it is what this app would reach for
if it had several screens sharing cached server state. It has none. What it needs
here — refetch when the query changes, do not let a stale response win, keep the
previous page visible while the next loads, report a failure — is roughly seventy
lines, and writing them makes the staleness rule *visible in the repository* rather
than delegated to a library's defaults.

The judgement to revisit: when a second and third feature start refetching the same
resources, or when cache invalidation after a mutation becomes a real question,
adopt the library and delete this hook. Importing a client, a provider and a cache
for one list is not that day.

### 4. Filter state is a reducer, and the page resets with it

`useGameFilters` owns `{result, from, to, event, page}` in a `useReducer`. Every
filter action sets `page: 0` in the same dispatch.

This is the rule the page exists to enforce, and it is why filter and page state
live together rather than as five `useState` calls beside a sixth. A user on page 5
who narrows the result set to eleven games must not be left looking at an empty
page 5 with no indication of why. Coupling the reset to the action, rather than to
an effect that watches the filters, means the two values can never be observed
disagreeing — there is no render in which the filters have changed and the page has
not.

`GamesPage` calls the hook and passes what it returns down. That is what the issue
means by the page coordinating page-level state without holding business logic.

### 5. Filters apply live, with the text input debounced

Selects and date inputs apply on change. `event` applies 300ms after typing stops,
via a shared `useDebouncedValue`.

An Apply button is the alternative and it is not wrong — it is predictable and it
costs no debounce machinery. It also puts a click between the user and every
change, and splits the state into draft and applied, which is the thing most likely
to drift out of sync later.

Debouncing only the free-text field is the part worth naming. A select has a finite
number of values and each change is a deliberate act; a text field produces a
request per keystroke, and `event` is the one filter #8 documents as always
scanning. The debounce lives in `src/hooks/shared/` because nothing about it is
about games.

The page is reset by the *raw* keystroke and the request fires from the *debounced*
value, so by the time a request for a new term goes out, `page` is already 0.

### 6. Stale responses are aborted, and the previous page stays on screen

Each fetch gets an `AbortController`; the effect's cleanup aborts it. An abort is
not an error state — the hook checks the signal before touching state.

Without this, a slow response for "Hastings" can land after a fast response for
"Hastings Premier" and repaint the table with results the filters no longer
describe. It is the one correctness problem in the page, and it is invisible in
local development, which is exactly why it is a decision rather than a detail.

While a refetch is in flight the hook stays in `ready` and raises a `refreshing`
flag, keeping the current rows visible under `aria-busy`. Dropping back to a
spinner on every page change makes the layout jump and loses the user's place. This
is `keepPreviousData` by hand, and it is four lines.

### 7. Types are hand-written now and generated later

`features/games/types/game.ts` mirrors `GameSummaryResponse`, `GamePageResponse` and
the three enums used by this feature (`GameResult`, `GameSource` and `GameColour`)
by hand, with a comment naming
[#27](https://github.com/guyAOgreen/Chess-App/issues/27) as the change that deletes
it.

There is no OpenAPI document to generate from yet. Hand-writing thirty lines of
types is not the cost; the risk is drift, and it is real — nothing fails when the
backend adds a field or renames one. It is bounded by the contract being frozen
(#8 settled it, #27 publishes it) and by the API module being the single place the
shape is spoken, so the drift has one place to be corrected.

Nullability follows the domain exactly, because a wrong optional is the drift that
bites: `white`, `black`, `result` and `source` are always present; `event`, `site`,
`round`, `playedOn`, `eco` and a side's `rating` are nullable; a side's `playerId`
and `name` are not.

### 8. `lib/api` grows a query builder and an honest failure type

Two changes, both shared:

`queryString(params)` builds a query string from a record, dropping `undefined`,
`null` and `''`. Filters are absent far more often than present, and the alternative
is conditional string assembly at each call site.

`getJson(path, options?)` accepts `{ signal?: AbortSignal }` so a caller can cancel
the request — decision 6 needs this and cannot work without it — and becomes a
discriminated result:

```ts
type JsonResponse<T> =
  | { kind: 'body'; ok: boolean; status: number; data: T }
  | { kind: 'invalid-body'; ok: boolean; status: number; message: string }
  | { kind: 'unreachable'; message: string };
```

Today it calls `response.json()` unconditionally, which is fine against the two
endpoints it has met and throws a `SyntaxError` the moment a proxy or a container
answers with HTML. That response is reachable but violates the expected JSON
contract, so it becomes `invalid-body`; an empty body is the same contract failure.
Only a rejected `fetch` becomes `unreachable`. The three cases are genuinely
different and a caller should be made to say which it means.

Not `RequestInit`. `getJson` sets its own `Accept` header, and a caller passing one
would silently replace it; the narrow options type makes the helper's one variable —
cancellation — the only thing a caller can vary. A request with a method and a body
is a different helper, written when `POST /api/games` gets a UI.

An aborted `fetch` rejects, so a cancelled request arrives here as `unreachable`.
`getJson` does not try to distinguish it: it cannot know whether the abort was
deliberate. `useGames` owns that policy and checks its own signal before touching
state.

Health keeps its unusual requirement — Actuator's 503 carries a meaningful body —
and expresses it as `kind === 'body'`, ignoring `ok`. It is the reason the union
splits on *whether there is a body* rather than on success. `fetchBackendHealth`
changes by a few lines; its component tests do not touch the network and are
unaffected.

### 9. CSS Modules, and the Vite template's centring goes

`*.module.css` beside each component, colours taken from the custom properties
already in `index.css` so light and dark keep working untouched.

Scoping is the argument. A global stylesheet that every feature edits is the
unrelated-responsibilities file CLAUDE.md warns about, and prefix-by-convention
plain CSS is that file with a naming rule nothing enforces. Modules are native to
Vite, cost no dependency, and make a class name a symbol the compiler checks.

`index.css` also has to change: `#root` is `1126px`, centred, with
`text-align: center`, which is the Vite starter template and not a layout a table
can live in. It becomes a max-width app shell with normal text alignment. This is
in scope because the page cannot be built without it — not a licence to restyle
anything else.

### 10. Sort is not sent

`sort` and `direction` are omitted from every request.

`GameSort` has exactly one value, and #8's default is already `PLAYED_ON DESC` —
newest first, which is what a games list should show. Sending the only value the
enum has, to select the default the server already applies, adds two fields to the
query type and changes nothing. Sortable columns are a later issue, and the
mechanism to support them exists on the backend.

### 11. Prev / Next, not a numbered pager

`GamePager` renders Previous and Next with a "Page 2 of 7 · 168 games" label, both
buttons disabled at the ends.

The envelope carries `totalPages` precisely so a client *can* draw a numbered pager,
and #8 chose offset paging partly to keep jump-to-page possible. Nothing in this
issue asks for it, a personal database of a few hundred games is a handful of pages,
and the numbers, the ellipsis and the current-page emphasis are real work. The data
to build one is already on screen the day it is wanted.

### 12. The date inputs bound each other

`from` sets the `min` of the `to` input, and `to` sets the `max` of `from`.

#8 rejects `from` after `to` with a 400 — a correct refusal of a client defect. The
client should not produce the defect. This matters more than it looks right now,
because [#43](https://github.com/guyAOgreen/Chess-App/issues/43) means that
rejection currently does not say which parameter was wrong, so the user would see a
generic failure for a mistake the form could have prevented.

## Module layout

```text
apps/web/src/
├── app/
│   └── App.tsx                        renders GamesPage; health card below
├── features/games/
│   ├── api/
│   │   └── games.ts                   fetchGames(query, signal) → GamePage
│   ├── types/
│   │   └── game.ts                    contract types; deleted by #27
│   ├── format.ts                      resultLabel, sourceLabel, sideLabel, EM_DASH
│   ├── hooks/
│   │   ├── useGames.ts                request state, abort, refresh, retry
│   │   └── useGameFilters.ts          filter + page reducer
│   ├── components/
│   │   ├── GameFilters.tsx            + GameFilters.module.css
│   │   ├── GameTable.tsx              + GameTable.module.css
│   │   ├── GameRow.tsx                one row; #11 makes it a link
│   │   └── GamePager.tsx              + GamePager.module.css
│   └── pages/
│       └── GamesPage.tsx              + GamesPage.module.css
├── hooks/shared/
│   └── useDebouncedValue.ts
├── lib/api.ts                         queryString + JsonResponse
└── index.css                          app shell; template centring removed
```

`format.ts` sits at the feature root rather than under `components/`: it is pure
domain-to-text mapping used by more than one component, it has its own unit test,
and it is not a component.

## Component contracts

Every component takes props and raises callbacks. None of them fetches, and none of
them knows a hook exists.

```ts
GameFilters  { values: GameFilterValues;
               onChange: <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => void;
               onClear: () => void }

GameTable    { games: GameSummary[] }          // non-empty; the page owns "none"
GameRow      { game: GameSummary }
GamePager    { page: number; totalElements: number; totalPages: number;
               onPageChange: (page: number) => void }
```

`GameTable` takes a non-empty list deliberately. "No games match these filters" and
"No games yet" are different sentences about the *page's* state, not two ways of
drawing a table, and deciding between them needs to know whether a filter is set —
which is the page's knowledge, not the table's.

`GameRow` exists as its own component for one reason: #11 turns it into a link, and
that should be one file.

## Data flow

```text
        ┌──────────────────┐
        │ useGameFilters   │  values (raw, for the inputs)
        │  reducer:        │  query  (debounced event, page)
        │  any filter →    │
        │  page = 0        │
        └────────┬─────────┘
                 │ query
                 ▼
        ┌──────────────────┐   fetchGames(gamesPath(query), signal)
        │ useGames         │ ─────────────────────────────▶ GET /api/games?…
        │  abort on change │
        │  keep prev page  │ ◀─────────────────────────────  GamePageResponse
        └────────┬─────────┘
                 │ state, retry
                 ▼
            GamesPage  ──▶ GameFilters │ GameTable │ GamePager
```

The two hooks' surfaces, and the types the page passes between them:

```ts
interface GameFilterValues {
  result?: GameResult;
  from?: string;              // ISO date, as the input yields it
  to?: string;
  event?: string;             // raw text; the query carries the debounced value
}

interface GamesQuery extends GameFilterValues {
  playerId?: string;          // no control yet — decision 2
  colour?: GameColour;
  page: number;
}

function useGameFilters(): {
  values: GameFilterValues;                     // drives the controlled inputs
  query: GamesQuery;                            // debounced, page-aware
  isFiltered: boolean;
  setFilter: <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => void;
  setPage: (page: number) => void;
  clear: () => void;
};
```

`values` and `query` differ in exactly one field. `values.event` updates on every
keystroke so the input stays responsive; `query.event` is the debounced value, so
the request waits. Keeping both on one hook is what stops a component from having
to know the debounce exists.

The API module splits into `gamesPath(query)`, which serialises, and
`fetchGames(path, signal)`, which requests. That split exists so `useGames` can key
its effect on the path. It is the honest dependency — the string *is* the request —
and being a string it sidesteps the object-identity trap where a query object built
fresh on every render refetches forever.

```ts
type GamesState =
  | { kind: 'loading' }
  | { kind: 'ready'; page: GamePage; refreshing: boolean }
  | { kind: 'failed'; message: string };

function useGames(query: GamesQuery): { state: GamesState; retry: () => void }
```

`retry` increments a counter the effect depends on, so a failed request can be
re-run without the user having to change a filter to provoke one.

The client never sends `size`, and never reads it back either: the pager needs the
current page, the total pages and the total games, and derives nothing from the page
size. One fewer parameter to keep in agreement.

## Page states

| State | What the page shows |
|---|---|
| `loading` | "Loading games…", filters visible and usable |
| `ready`, rows | Table + pager; `aria-busy` on the table while `refreshing` |
| `ready`, no rows, no filters set | "No games yet." with a note that PGN import is the way in |
| `ready`, no rows, filters set | "No games match these filters." + Clear filters |
| `failed` | The failure message + Retry |

The filter form is mounted and interactive in all five. A user who filters into an
empty result must be able to filter back out, and a user whose request failed must
be able to change it — a page that replaces its own controls with an error message
traps them.

The two empty states are distinguished by whether any filter is set, which is why
`useGameFilters` exposes `isFiltered`. "No games match these filters" shown to
someone with an empty database is a small lie that sends them looking for a filter
they never set.

## Rendering detail

A row is: White (rating) — Black (rating), result, date, event, site, round, ECO,
source.

* `result` renders as its PGN token — `1-0`, `0-1`, `½-½`, `*` — because that is
  what a chess player reads. The mapping is a record in `format.ts`, not a switch in
  JSX.
* A side renders as `name` plus the rating in parentheses when one was recorded.
* Absent optional metadata renders as an em dash. An empty cell reads as a broken
  table; an em dash reads as "not recorded".
* `playedOn` renders as the ISO string the API returns. `Intl.DateTimeFormat` would
  be prettier and would make every assertion depend on the runner's locale;
  `2024-05-01` is unambiguous, and it is what the PGN `Date` tag holds.
* `source` renders as a humanised label — `PGN_IMPORT` → "PGN import".

The event input has `maxLength={255}`, matching the API boundary rather than
allowing the form to create a request the backend must reject.

Accessibility: the filters are a `<form>` with a `<label>` per control; the result
count and the empty message live in a `role="status"` region so a screen reader is
told the list changed; the table wrapper carries `aria-busy` during a refresh. The
table scrolls horizontally inside its wrapper rather than collapsing to cards —
nine columns do not fit a phone, and designing a mobile layout for a screen nobody
has used is guesswork.

## Testing

Vitest and React Testing Library, as the existing tests do — behaviour through the
public surface, not internals. `fetch` is stubbed with `vi.stubGlobal`; no test
touches the network.

### `useDebouncedValue.test.ts`

Fake timers. The value settles only after the delay; a change during the wait
restarts it; unmounting mid-wait does not set state.

### `useGames.test.ts`

* `loading` first, then `ready` with the page.
* Changing the query refetches, and the request URL carries the filters.
* **A superseded request never lands.** Resolve a slow first response *after* a fast
  second one and assert the second's rows are on screen. This is decision 6, and it
  is the one test that would catch the bug the decision exists to prevent.
* An aborted request does not produce a `failed` state.
* A transport failure and a non-2xx both produce `failed`; `retry` re-requests.
* The previous page stays readable while a refetch is in flight.

### `useGameFilters.test.ts`

Changing any filter resets the page to 0. Changing the page does not disturb the
filters. `clear` empties every filter and returns to page 0. `isFiltered` is false
only when nothing is set.

### `GameFilters.test.tsx`

Driven with `user-event`: each control raises `onChange` with the right key and a
parsed value; an emptied text field raises `undefined` rather than `''`; the event
input enforces the 255-character limit; the date inputs carry each other's bound in
the correct direction; Clear raises `onClear`.

### `GameTable.test.tsx` / `GameRow.test.tsx`

Rows render in the order given; an em dash appears for each absent optional field; a
side without a rating omits the parentheses; the result tokens are right.

### `format.test.ts`

Every `GameResult` and `GameSource` value has a label. Written as a loop over the
enum so a value added later fails the test rather than rendering raw.

### `GamesPage.test.tsx`

One integration test with a stubbed `fetch`: the page loads, the user types an event
and the debounce elapses, the request URL contains the term and `page=0`, and the
returned rows render. Plus both empty states and the failure state.

### `lib/api.test.ts`

`queryString` drops `undefined`, `null` and `''`, keeps `0`, and encodes. `getJson`
returns `body` for JSON at any status, `invalid-body` for an HTML or empty response,
and `unreachable` when `fetch` rejects.

### Not tested

The CSS. Module class names are asserted by nothing, and a test that a heading has a
class is a test of the implementation.

## Risks

**The contract is hand-copied.** Decision 7. Until #27 generates these types, a
backend field rename breaks the page at runtime and nothing before it. The blast
radius is one file and the contract is frozen, but this is the cost being accepted.

**The template stylesheet changes under the health card.** Decision 9 removes the
centring `#root` rules, so the existing card's appearance changes. It is the only
other thing on the page, its tests assert text rather than layout, and the change is
visually checked when the page is run.

**A rejected request cannot say what was wrong.** #43. Decisions 2 and 12 plus the
event input's length limit remove every rejection the form could provoke, and
`size` and `page` are not user-editable, so a 400 should be unreachable from the UI.
If one arrives the user sees a generic message — which is the honest state of the
API today, and improves for free when #43 lands.

**No authentication.** [#25](https://github.com/guyAOgreen/Chess-App/issues/25).
The page lists every game in the database to anyone who opens it, because the
endpoint does. Same condition the backend recorded: not to be exposed publicly ahead
of authentication.

## Known limitations

**Filters do not survive a reload or the back button.** State is in React, not the
URL. Deep-linking a filtered list is genuinely useful and it wants a router to hold
the search params, which arrives with #11. Recorded so that #11 picks it up rather
than rediscovering it.

**Offset paging can shift rows mid-browse.** Inherited from #8, which explains why
the trade is accepted. The page does nothing to make it worse and nothing to hide
it.

**Dates render in ISO form.** Decision above; a locale-aware format is a
one-function change when someone objects.

**Below about 700px the table scrolls sideways.** Deliberate, and the first thing to
revisit when the app is used on a phone.

**The debounce is time-based, not request-based.** A user typing steadily slower
than 300ms per keystroke issues a request per keystroke. Correctness is unaffected —
decision 6 aborts the losers — and the fix, if one is ever wanted, is a longer delay.

## Out of scope

* **Player and colour filters** —
  [#21](https://github.com/guyAOgreen/Chess-App/issues/21). Decision 2; the query
  type already carries the fields.
* **Routing, and a row that opens a game** —
  [#11](https://github.com/guyAOgreen/Chess-App/issues/11). Decision 1. #11 also
  inherits the URL-state limitation above.
* **The game viewer itself** — board, move list, navigation: #11.
* **Generated API types and an OpenAPI document** —
  [#27](https://github.com/guyAOgreen/Chess-App/issues/27), which deletes
  `types/game.ts`.
* **Sortable columns** — decision 10. One enum value on the backend and a click
  target here.
* **Filtering by source, site, round, ECO or rating** — not in the issue; each is
  one control and one predicate when something needs it.
* **A PGN import screen.** `POST /api/games` exists and has no UI. It is what the
  "No games yet" message points at, and it is not this issue.
* **Field-level rejection messages** —
  [#43](https://github.com/guyAOgreen/Chess-App/issues/43).
* **Authentication and per-user scoping** —
  [#25](https://github.com/guyAOgreen/Chess-App/issues/25).
* **A shared component library.** Nothing here is reused yet. The first component a
  second feature wants moves to `components/shared/` then.
