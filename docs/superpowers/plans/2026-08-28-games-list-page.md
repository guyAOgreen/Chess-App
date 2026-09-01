# Games List Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `apps/web/src/features/games` — a games list page with result, date-range and event filters, pagination, and no business logic in its components.

**Architecture:** A feature module in the `api/ types/ hooks/ components/ pages/` shape the `system-health` feature established. Two hooks carry all the state: `useGameFilters` holds filters and the page number in one reducer so a filter change always resets the page, and `useGames` turns the resulting query into a request, aborting the previous one so a late response cannot repaint the table. Components take props and raise callbacks; the page composes them. No router, no data-fetching library, no new dependencies.

**Tech Stack:** React 19, TypeScript ~6.0, Vite 8, Vitest 4 + React Testing Library, CSS Modules (native to Vite), Yarn classic. All commands run from `apps/web`.

**Spec:** [`docs/superpowers/specs/2026-08-28-games-list-page-design.md`](../specs/2026-08-28-games-list-page-design.md)

**Issue:** [#10](https://github.com/guyAOgreen/Chess-App/issues/10) — M1, Game database

## Global Constraints

- **No new dependencies.** `package.json` gains nothing. React and React DOM remain the only runtime dependencies.
- **The backend does not change.** `GET /api/games` is finished; its contract is [#8's spec](../specs/2026-08-26-game-list-endpoint-design.md).
- **Request parameters, exactly:** `playerId`, `colour`, `result`, `from`, `to`, `event`, `page`. `sort`, `direction` and `size` are never sent (spec decisions 10 and the data-flow section).
- **Vite dev-server proxy already forwards `/api` unrewritten**, so paths are `/api/games`, relative, never absolute.
- **Every test stubs `fetch`.** No test touches the network.
- **TDD.** The failing test comes first in every task, and you run it and watch it fail before writing implementation.
- **Commit at the end of every task**, after `yarn test`, `yarn lint` and `yarn build` all pass.
- **Commit message style:** a sentence-case subject naming what changed, then a blank line and a short body. Match the repository's existing log. End every commit message with:
  `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`

## Per-Task Workflow

Each task is implemented by Sonnet and reviewed by Opus before it is signed off:

1. Sonnet implements the task's steps in order, TDD, ending with tests/lint/build green.
2. **Opus reviews the diff before the commit is accepted** — against this plan, the spec, and CLAUDE.md. A rejected task is reworked before the next task starts.
3. Only then does the next task begin.

Do not batch tasks. Each one ends at a green build and a reviewable diff.

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `src/hooks/shared/useDebouncedValue.ts` | Generic value debounce. Nothing game-specific. |
| `src/features/games/types/game.ts` | The API contract as TypeScript. Deleted by #27. |
| `src/features/games/api/games.ts` | `gamesPath(query)` and `fetchGames(path, signal)`. The only place wire parameter names are spoken. |
| `src/features/games/format.ts` | Pure domain-to-text: result tokens, source labels, side labels, em dash. |
| `src/features/games/hooks/useGameFilters.ts` | Filter + page reducer; exposes raw `values` and the debounced `query`. |
| `src/features/games/hooks/useGames.ts` | Request state machine: loading / ready(+refreshing) / failed, abort, retry. |
| `src/features/games/components/GameRow.tsx` | One `<tr>`. #11 turns it into a link. |
| `src/features/games/components/GameTable.tsx` | The `<table>`. Takes a non-empty list. |
| `src/features/games/components/GameFilters.tsx` | The controlled filter form. |
| `src/features/games/components/GamePager.tsx` | Previous / Next + position label. |
| `src/features/games/pages/GamesPage.tsx` | Composes the two hooks and the three components; owns the empty and failure states. |
| `*.module.css` beside each component and the page | Scoped styles using the existing custom properties. |

**Modified:**

| File | Change |
|---|---|
| `src/lib/api.ts` | `queryString`; `getJson` gains a signal and a discriminated result. |
| `src/features/system-health/api/health.ts` | Adapts to the new `getJson` result. |
| `src/app/App.tsx` | Renders `GamesPage` above the health card. |
| `src/app/App.test.tsx` | Stubs `fetch` now that the app fetches on mount. |
| `src/index.css` | `#root` becomes a max-width shell; the template's centring goes. |

---

### Task 1: Shared query builder and JSON result

The whole app's HTTP boundary. Every later task depends on this, so it goes first.

**Files:**
- Modify: `apps/web/src/lib/api.ts` (replace the file's contents)
- Modify: `apps/web/src/features/system-health/api/health.ts:12-14`
- Test: `apps/web/src/lib/api.test.ts` (create)

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `queryString(params: QueryParams): string` — `''` or `'?a=1&b=2'`
  - `type QueryParams = Record<string, string | number | null | undefined>`
  - `getJson<T>(path: string, options?: GetJsonOptions): Promise<JsonResponse<T>>`
  - `interface GetJsonOptions { signal?: AbortSignal }`
  - `type JsonResponse<T>` with `kind: 'body' | 'invalid-body' | 'unreachable'`

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/lib/api.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getJson, queryString } from './api';

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

function nonJsonResponse(status: number): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      throw new SyntaxError('Unexpected token < in JSON at position 0');
    },
  } as unknown as Response;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('queryString', () => {
  it('is empty when nothing is set', () => {
    expect(queryString({})).toBe('');
    expect(queryString({ a: undefined, b: null, c: '' })).toBe('');
  });

  it('keeps zero, which is a page number and not an absent value', () => {
    expect(queryString({ page: 0 })).toBe('?page=0');
  });

  it('encodes values', () => {
    expect(queryString({ event: 'Hastings Premier' })).toBe('?event=Hastings+Premier');
  });

  it('drops only the absent entries', () => {
    expect(queryString({ result: 'DRAW', event: undefined, page: 2 })).toBe('?result=DRAW&page=2');
  });
});

describe('getJson', () => {
  it('returns the body of a successful response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'UP' })));

    const response = await getJson<{ status: string }>('/actuator/health');

    expect(response).toEqual({ kind: 'body', ok: true, status: 200, data: { status: 'UP' } });
  });

  it('returns the body of a failed response, because some endpoints answer in it', async () => {
    // Actuator answers 503 with a well-formed body when a component is DOWN.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'DOWN' }, 503)));

    const response = await getJson<{ status: string }>('/actuator/health');

    expect(response).toEqual({ kind: 'body', ok: false, status: 503, data: { status: 'DOWN' } });
  });

  it('reports a response that is not JSON as an invalid body, not as a dead network', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(nonJsonResponse(502)));

    const response = await getJson('/api/games');

    expect(response.kind).toBe('invalid-body');
    if (response.kind === 'invalid-body') {
      expect(response.status).toBe(502);
      expect(response.ok).toBe(false);
    }
  });

  it('reports a rejected fetch as unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const response = await getJson('/api/games');

    expect(response).toEqual({ kind: 'unreachable', message: 'Failed to fetch' });
  });

  it("passes the caller's abort signal to fetch", async () => {
    const fetchStub = vi.fn().mockResolvedValue(jsonResponse({}));
    vi.stubGlobal('fetch', fetchStub);
    const controller = new AbortController();

    await getJson('/api/games', { signal: controller.signal });

    expect(fetchStub.mock.calls[0][1]).toMatchObject({ signal: controller.signal });
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/lib/api.test.ts`
Expected: FAIL — `queryString` is not exported, and the `getJson` assertions fail against the old `{ok, status, data}` shape.

- [ ] **Step 3: Rewrite `src/lib/api.ts`**

Replace the whole file:

```ts
export type QueryParams = Record<string, string | number | null | undefined>;

/**
 * Absent and blank mean the same thing to this API, so both are dropped rather
 * than sent as an empty parameter. Zero is a value — it is page one.
 */
export function queryString(params: QueryParams): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    search.set(key, String(value));
  }
  const query = search.toString();
  return query === '' ? '' : `?${query}`;
}

/**
 * Three outcomes, because they mean three different things and a caller should
 * have to say which it is handling.
 *
 * <p>`body` is a response carrying JSON, whatever its status: Actuator health and
 * problem+json rejections both put the answer in the body of a non-2xx.
 * `invalid-body` is a response that arrived and was not JSON — an HTML error page
 * from a proxy, or an empty body. `unreachable` is a `fetch` that never produced a
 * response at all.
 */
export type JsonResponse<T> =
  | { kind: 'body'; ok: boolean; status: number; data: T }
  | { kind: 'invalid-body'; ok: boolean; status: number; message: string }
  | { kind: 'unreachable'; message: string };

/** Deliberately not `RequestInit`: `getJson` owns its `Accept` header, and a
 * caller supplying one would silently replace it. Cancellation is the only thing
 * a caller varies. A request with a method and a body is a different helper. */
export interface GetJsonOptions {
  signal?: AbortSignal;
}

export async function getJson<T>(
  path: string,
  options: GetJsonOptions = {},
): Promise<JsonResponse<T>> {
  let response: Response;
  try {
    response = await fetch(path, {
      headers: { Accept: 'application/json' },
      signal: options.signal,
    });
  } catch (error: unknown) {
    // An aborted request lands here too. `getJson` cannot know whether the abort
    // was deliberate, so it does not try to; the caller checks its own signal.
    return { kind: 'unreachable', message: messageOf(error) };
  }

  try {
    const data = (await response.json()) as T;
    return { kind: 'body', ok: response.ok, status: response.status, data };
  } catch (error: unknown) {
    return {
      kind: 'invalid-body',
      ok: response.ok,
      status: response.status,
      message: messageOf(error),
    };
  }
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
```

- [ ] **Step 4: Adapt the health feature**

In `apps/web/src/features/system-health/api/health.ts`, replace the body of `fetchBackendHealth`:

```ts
/**
 * Actuator answers 503 with a well-formed body when a component is DOWN, so the
 * body is used whether or not the status is 2xx. Anything that is not a body at
 * all is thrown, and `useBackendHealth`'s catch turns it into `unreachable`.
 */
export async function fetchBackendHealth(): Promise<BackendHealth> {
  const response = await getJson<BackendHealth>('/actuator/health');
  if (response.kind !== 'body') {
    throw new Error(response.message);
  }
  return response.data;
}
```

- [ ] **Step 5: Run the full suite**

Run: `yarn test`
Expected: PASS — the new `api.test.ts` and the existing `BackendHealthCard` and `App` tests.

- [ ] **Step 6: Lint and typecheck**

Run: `yarn lint && yarn build`
Expected: no lint errors, build succeeds.

- [ ] **Step 7: Commit**

```bash
git add src/lib/api.ts src/lib/api.test.ts src/features/system-health/api/health.ts
git commit -m "Shared query builder and a three-way JSON result (#10)"
```

---

### Task 2: `useDebouncedValue`

**Files:**
- Create: `apps/web/src/hooks/shared/useDebouncedValue.ts`
- Test: `apps/web/src/hooks/shared/useDebouncedValue.test.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `useDebouncedValue<T>(value: T, delayMs: number): T`

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/hooks/shared/useDebouncedValue.test.ts`:

```ts
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDebouncedValue } from './useDebouncedValue';

describe('useDebouncedValue', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('reports the first value without waiting', () => {
    const { result } = renderHook(() => useDebouncedValue('Hastings', 300));

    expect(result.current).toBe('Hastings');
  });

  it('withholds a new value until the delay has passed', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'Hastings' },
    });

    rerender({ value: 'Hastings Premier' });
    expect(result.current).toBe('Hastings');

    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(result.current).toBe('Hastings Premier');
  });

  it('restarts the delay when the value changes again', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'H' },
    });

    rerender({ value: 'Ha' });
    act(() => {
      vi.advanceTimersByTime(200);
    });
    rerender({ value: 'Has' });
    act(() => {
      vi.advanceTimersByTime(200);
    });

    // 400ms have passed, but only 200ms since the last change.
    expect(result.current).toBe('H');

    act(() => {
      vi.advanceTimersByTime(100);
    });
    expect(result.current).toBe('Has');
  });

  it('does not settle a value after unmounting', () => {
    const { rerender, unmount } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'a' },
    });

    rerender({ value: 'b' });
    unmount();

    expect(() => {
      act(() => {
        vi.advanceTimersByTime(300);
      });
    }).not.toThrow();
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/hooks/shared/useDebouncedValue.test.ts`
Expected: FAIL — cannot resolve `./useDebouncedValue`.

- [ ] **Step 3: Write the hook**

Create `apps/web/src/hooks/shared/useDebouncedValue.ts`:

```ts
import { useEffect, useState } from 'react';

/**
 * The value, held back until it has stopped changing for `delayMs`.
 *
 * <p>Shared rather than living with the games feature: nothing about waiting for
 * a value to settle is about games. The timer is cleared on every change and on
 * unmount, so only the last value in a burst is ever reported.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [settled, setSettled] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return settled;
}
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/hooks/shared/useDebouncedValue.test.ts`
Expected: PASS — four tests.

- [ ] **Step 5: Lint, build, commit**

```bash
yarn lint && yarn build
git add src/hooks/shared/useDebouncedValue.ts src/hooks/shared/useDebouncedValue.test.ts
git commit -m "Shared useDebouncedValue hook (#10)"
```

---

### Task 3: The contract types and the games API module

**Files:**
- Create: `apps/web/src/features/games/types/game.ts`
- Create: `apps/web/src/features/games/api/games.ts`
- Test: `apps/web/src/features/games/api/games.test.ts`

**Interfaces:**
- Consumes: `queryString`, `getJson`, `JsonResponse` from Task 1.
- Produces:
  - `GAME_RESULTS`, `GAME_SOURCES` (readonly arrays), `GameResult`, `GameSource`, `GameColour`
  - `GameSide`, `GameSummary`, `GamePage`, `GameFilterValues`, `GamesQuery`
  - `gamesPath(query: GamesQuery): string`
  - `fetchGames(path: string, signal?: AbortSignal): Promise<GamePage>`
  - `class GamesRequestFailed extends Error`

**Why `gamesPath` and `fetchGames` are separate:** `useGames` (Task 6) keys its effect on the path string. The string *is* the request, so it is the honest dependency, and it sidesteps the trap where a fresh query object each render refetches forever.

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/api/games.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchGames, gamesPath, GamesRequestFailed } from './games';
import type { GamePage } from '../types/game';

const EMPTY_PAGE: GamePage = {
  content: [],
  page: 0,
  size: 25,
  totalElements: 0,
  totalPages: 0,
};

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('gamesPath', () => {
  it('sends only the page when nothing is filtered', () => {
    expect(gamesPath({ page: 0 })).toBe('/api/games?page=0');
  });

  it('sends every filter that is set', () => {
    const path = gamesPath({
      result: 'DRAW',
      from: '2024-01-01',
      to: '2024-12-31',
      event: 'Hastings',
      page: 2,
    });

    expect(path).toBe(
      '/api/games?result=DRAW&from=2024-01-01&to=2024-12-31&event=Hastings&page=2',
    );
  });

  it('never sends sort, direction or size', () => {
    // The backend defaults to PLAYED_ON DESC and size 25; sending the only value
    // GameSort has would say nothing. See spec decision 10.
    const path = gamesPath({ page: 0 });

    expect(path).not.toContain('sort');
    expect(path).not.toContain('direction');
    expect(path).not.toContain('size');
  });

  it('carries the filters #21 will add, when something sets them', () => {
    const path = gamesPath({
      playerId: '11111111-1111-1111-1111-111111111111',
      colour: 'WHITE',
      page: 0,
    });

    expect(path).toContain('playerId=11111111-1111-1111-1111-111111111111');
    expect(path).toContain('colour=WHITE');
  });
});

describe('fetchGames', () => {
  it('returns the page', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_PAGE)));

    await expect(fetchGames('/api/games?page=0')).resolves.toEqual(EMPTY_PAGE);
  });

  it('fails when the server rejects the request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ title: 'Bad Request' }, 400)));

    await expect(fetchGames('/api/games?page=0')).rejects.toBeInstanceOf(GamesRequestFailed);
  });

  it('fails when the response is not JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 502,
        json: async () => {
          throw new SyntaxError('Unexpected token <');
        },
      } as unknown as Response),
    );

    await expect(fetchGames('/api/games?page=0')).rejects.toBeInstanceOf(GamesRequestFailed);
  });

  it('fails, reporting the transport message, when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(fetchGames('/api/games?page=0')).rejects.toThrow('Failed to fetch');
  });

  it('passes the abort signal through', async () => {
    const fetchStub = vi.fn().mockResolvedValue(jsonResponse(EMPTY_PAGE));
    vi.stubGlobal('fetch', fetchStub);
    const controller = new AbortController();

    await fetchGames('/api/games?page=0', controller.signal);

    expect(fetchStub.mock.calls[0][1]).toMatchObject({ signal: controller.signal });
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/api/games.test.ts`
Expected: FAIL — cannot resolve `./games`.

- [ ] **Step 3: Write the types**

Create `apps/web/src/features/games/types/game.ts`:

```ts
/**
 * The `GET /api/games` contract, hand-written.
 *
 * <p>Mirrors `GameSummaryResponse` and `GamePageResponse` in the backend's
 * `com.chessapp.game.api` package. Nothing enforces the correspondence — that is
 * what [#27](https://github.com/guyAOgreen/Chess-App/issues/27) fixes, by
 * generating this file from an OpenAPI document and deleting it from source
 * control. Until then this is the single place the shape is spoken, so drift has
 * one place to be corrected.
 *
 * <p>The arrays exist so a test can iterate every value; the types are derived
 * from them so the two cannot disagree.
 */

export const GAME_RESULTS = ['WHITE_WON', 'BLACK_WON', 'DRAW', 'UNFINISHED'] as const;
export type GameResult = (typeof GAME_RESULTS)[number];

export const GAME_SOURCES = [
  'PERSONAL',
  'CLUB',
  'PGN_IMPORT',
  'LICHESS',
  'CHESS_COM',
  'MEGA_DATABASE',
  'OTHER',
] as const;
export type GameSource = (typeof GAME_SOURCES)[number];

export type GameColour = 'WHITE' | 'BLACK';

/** One colour's share of the game. `name` is the game-time snapshot. */
export interface GameSide {
  playerId: string;
  name: string;
  rating: number | null;
}

/**
 * A game as a row of the list. No `movetext` — the detail endpoint carries that.
 *
 * <p>Nullability follows the backend domain exactly: `white`, `black`, `result`
 * and `source` are always present, and the rest were optional in the document the
 * game was imported from.
 */
export interface GameSummary {
  id: string;
  white: GameSide;
  black: GameSide;
  event: string | null;
  site: string | null;
  round: string | null;
  playedOn: string | null;
  result: GameResult;
  eco: string | null;
  source: GameSource;
}

export interface GamePage {
  content: GameSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** What the filter form edits. */
export interface GameFilterValues {
  result?: GameResult;
  from?: string;
  to?: string;
  event?: string;
}

/**
 * What gets requested. `playerId` and `colour` have no control yet — the endpoint
 * takes a UUID and nothing turns a name into one until
 * [#21](https://github.com/guyAOgreen/Chess-App/issues/21), which adds the control
 * and nothing else.
 */
export interface GamesQuery extends GameFilterValues {
  playerId?: string;
  colour?: GameColour;
  page: number;
}
```

- [ ] **Step 4: Write the API module**

Create `apps/web/src/features/games/api/games.ts`:

```ts
import { getJson, queryString } from '../../../lib/api';
import type { GamePage, GamesQuery } from '../types/game';

/** A request that did not produce a page, whatever the reason. The page shows the
 * message; there is nothing else it can usefully do with the distinction, and #43
 * is what makes a rejection able to explain itself. */
export class GamesRequestFailed extends Error {}

/**
 * The request as a string. Kept separate from {@link fetchGames} so `useGames` can
 * use it as its effect's dependency: the path *is* the request, which makes it the
 * honest thing to key on, and a string cannot change identity on every render the
 * way a fresh query object does.
 *
 * <p>The wire parameter names are written out here rather than spread from the
 * query, so this is the one file to read to know what is sent.
 */
export function gamesPath(query: GamesQuery): string {
  return `/api/games${queryString({
    playerId: query.playerId,
    colour: query.colour,
    result: query.result,
    from: query.from,
    to: query.to,
    event: query.event,
    page: query.page,
  })}`;
}

export async function fetchGames(path: string, signal?: AbortSignal): Promise<GamePage> {
  const response = await getJson<GamePage>(path, { signal });

  switch (response.kind) {
    case 'body':
      if (response.ok) {
        return response.data;
      }
      throw new GamesRequestFailed(`The server rejected the request (${response.status}).`);
    case 'invalid-body':
      throw new GamesRequestFailed(
        `The server answered ${response.status} with something that is not JSON.`,
      );
    case 'unreachable':
      throw new GamesRequestFailed(response.message);
  }
}
```

- [ ] **Step 5: Run the test**

Run: `yarn test src/features/games/api/games.test.ts`
Expected: PASS — nine tests.

- [ ] **Step 6: Lint, build, commit**

```bash
yarn lint && yarn build
git add src/features/games/types/game.ts src/features/games/api/games.ts src/features/games/api/games.test.ts
git commit -m "Games list contract types and API module (#10)"
```

---

### Task 4: Display formatting

**Files:**
- Create: `apps/web/src/features/games/format.ts`
- Test: `apps/web/src/features/games/format.test.ts`

**Interfaces:**
- Consumes: `GameResult`, `GameSource`, `GameSide`, `GAME_RESULTS`, `GAME_SOURCES` from Task 3.
- Produces: `EM_DASH`, `resultLabel(result)`, `sourceLabel(source)`, `sideLabel(side)`, `orDash(value)`

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/format.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { EM_DASH, orDash, resultLabel, sideLabel, sourceLabel } from './format';
import { GAME_RESULTS, GAME_SOURCES } from './types/game';

describe('resultLabel', () => {
  it('renders the PGN token, which is what a chess player reads', () => {
    expect(resultLabel('WHITE_WON')).toBe('1-0');
    expect(resultLabel('BLACK_WON')).toBe('0-1');
    expect(resultLabel('DRAW')).toBe('½-½');
    expect(resultLabel('UNFINISHED')).toBe('*');
  });

  it('has a label for every result', () => {
    // A value added to the backend enum fails here rather than rendering raw.
    for (const result of GAME_RESULTS) {
      expect(resultLabel(result)).toBeTruthy();
    }
  });
});

describe('sourceLabel', () => {
  it('humanises the enum constant', () => {
    expect(sourceLabel('PGN_IMPORT')).toBe('PGN import');
    expect(sourceLabel('CHESS_COM')).toBe('Chess.com');
  });

  it('has a label for every source', () => {
    for (const source of GAME_SOURCES) {
      expect(sourceLabel(source)).toBeTruthy();
    }
  });
});

describe('sideLabel', () => {
  it('shows the rating when one was recorded', () => {
    expect(sideLabel({ playerId: 'a', name: 'Carlsen, M', rating: 2839 })).toBe(
      'Carlsen, M (2839)',
    );
  });

  it('omits the parentheses when no rating was recorded', () => {
    expect(sideLabel({ playerId: 'a', name: 'Carlsen, M', rating: null })).toBe('Carlsen, M');
  });
});

describe('orDash', () => {
  it('renders absent metadata as an em dash, because an empty cell reads as broken', () => {
    expect(orDash(null)).toBe(EM_DASH);
    expect(orDash('Hastings')).toBe('Hastings');
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/format.test.ts`
Expected: FAIL — cannot resolve `./format`.

- [ ] **Step 3: Write the module**

Create `apps/web/src/features/games/format.ts`:

```ts
import type { GameResult, GameSide, GameSource } from './types/game';

/** Absent metadata. An empty cell reads as a broken table; this reads as
 * "not recorded". */
export const EM_DASH = '—';

/** The records are typed by the enum, so a value added to `GameResult` or
 * `GameSource` is a compile error here rather than a blank cell. */
const RESULT_LABELS: Record<GameResult, string> = {
  WHITE_WON: '1-0',
  BLACK_WON: '0-1',
  DRAW: '½-½',
  UNFINISHED: '*',
};

const SOURCE_LABELS: Record<GameSource, string> = {
  PERSONAL: 'Personal',
  CLUB: 'Club',
  PGN_IMPORT: 'PGN import',
  LICHESS: 'Lichess',
  CHESS_COM: 'Chess.com',
  MEGA_DATABASE: 'Mega Database',
  OTHER: 'Other',
};

export function resultLabel(result: GameResult): string {
  return RESULT_LABELS[result];
}

export function sourceLabel(source: GameSource): string {
  return SOURCE_LABELS[source];
}

export function sideLabel(side: GameSide): string {
  return side.rating === null ? side.name : `${side.name} (${side.rating})`;
}

export function orDash(value: string | null): string {
  return value ?? EM_DASH;
}
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/features/games/format.test.ts`
Expected: PASS — seven tests.

- [ ] **Step 5: Lint, build, commit**

```bash
yarn lint && yarn build
git add src/features/games/format.ts src/features/games/format.test.ts
git commit -m "Games display formatting (#10)"
```

---

### Task 5: `useGameFilters`

The reducer that makes a filter change reset the page.

**Files:**
- Create: `apps/web/src/features/games/hooks/useGameFilters.ts`
- Test: `apps/web/src/features/games/hooks/useGameFilters.test.ts`

**Interfaces:**
- Consumes: `useDebouncedValue` (Task 2); `GameFilterValues`, `GamesQuery` (Task 3).
- Produces:

```ts
interface UseGameFilters {
  values: GameFilterValues;
  query: GamesQuery;
  isFiltered: boolean;
  setFilter: <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => void;
  setPage: (page: number) => void;
  clear: () => void;
}
function useGameFilters(): UseGameFilters
```

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/hooks/useGameFilters.test.ts`:

```ts
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useGameFilters } from './useGameFilters';

describe('useGameFilters', () => {
  it('starts unfiltered on the first page', () => {
    const { result } = renderHook(() => useGameFilters());

    expect(result.current.values).toEqual({});
    expect(result.current.query.page).toBe(0);
    expect(result.current.isFiltered).toBe(false);
  });

  it('keeps the page the user asked for', () => {
    const { result } = renderHook(() => useGameFilters());

    act(() => result.current.setPage(3));

    expect(result.current.query.page).toBe(3);
    expect(result.current.values).toEqual({});
  });

  it('returns to the first page whenever a filter changes', () => {
    // The rule this hook exists for: page 5 of a result set that no longer has
    // five pages is an empty screen with no explanation.
    const { result } = renderHook(() => useGameFilters());

    act(() => result.current.setPage(5));
    act(() => result.current.setFilter('result', 'DRAW'));

    expect(result.current.query.page).toBe(0);
    expect(result.current.values.result).toBe('DRAW');
  });

  it('clears every filter and returns to the first page', () => {
    const { result } = renderHook(() => useGameFilters());

    act(() => result.current.setFilter('result', 'DRAW'));
    act(() => result.current.setFilter('from', '2024-01-01'));
    act(() => result.current.setPage(2));
    act(() => result.current.clear());

    expect(result.current.values).toEqual({});
    expect(result.current.query.page).toBe(0);
    expect(result.current.isFiltered).toBe(false);
  });

  it('is unfiltered again when the only filter is emptied', () => {
    const { result } = renderHook(() => useGameFilters());

    act(() => result.current.setFilter('result', 'DRAW'));
    expect(result.current.isFiltered).toBe(true);

    act(() => result.current.setFilter('result', undefined));
    expect(result.current.isFiltered).toBe(false);
  });

  describe('the event term', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('appears in values at once and in the query only once typing settles', () => {
      const { result } = renderHook(() => useGameFilters());

      act(() => result.current.setFilter('event', 'Hast'));

      expect(result.current.values.event).toBe('Hast');
      expect(result.current.query.event).toBeUndefined();

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(result.current.query.event).toBe('Hast');
      // The page was already reset by the keystroke, so the request that finally
      // goes out asks for page 0.
      expect(result.current.query.page).toBe(0);
    });
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/hooks/useGameFilters.test.ts`
Expected: FAIL — cannot resolve `./useGameFilters`.

- [ ] **Step 3: Write the hook**

Create `apps/web/src/features/games/hooks/useGameFilters.ts`:

```ts
import { useCallback, useMemo, useReducer } from 'react';
import { useDebouncedValue } from '../../../hooks/shared/useDebouncedValue';
import type { GameFilterValues, GamesQuery } from '../types/game';

const EVENT_DEBOUNCE_MS = 300;

interface FilterState {
  values: GameFilterValues;
  page: number;
}

type FilterAction =
  | { type: 'filter'; patch: Partial<GameFilterValues> }
  | { type: 'page'; page: number }
  | { type: 'clear' };

const INITIAL: FilterState = { values: {}, page: 0 };

/**
 * Every filter action returns to page 0 in the same dispatch. Doing it here
 * rather than in an effect that watches the filters means there is no render in
 * which the filters have changed and the page has not.
 */
function reduce(state: FilterState, action: FilterAction): FilterState {
  switch (action.type) {
    case 'filter':
      return { values: { ...state.values, ...action.patch }, page: 0 };
    case 'page':
      return { ...state, page: action.page };
    case 'clear':
      return INITIAL;
  }
}

export interface UseGameFilters {
  /** Raw, for the controlled inputs: updates on every keystroke. */
  values: GameFilterValues;
  /** What to request: the same filters with the event term debounced. */
  query: GamesQuery;
  isFiltered: boolean;
  setFilter: <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => void;
  setPage: (page: number) => void;
  clear: () => void;
}

/**
 * Filter and page state in one place, so a component never has to know that the
 * event term is debounced or that changing a filter moves the page.
 */
export function useGameFilters(): UseGameFilters {
  const [state, dispatch] = useReducer(reduce, INITIAL);
  const settledEvent = useDebouncedValue(state.values.event, EVENT_DEBOUNCE_MS);

  const setFilter = useCallback(
    <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => {
      dispatch({ type: 'filter', patch: { [key]: value } as Partial<GameFilterValues> });
    },
    [],
  );

  const setPage = useCallback((page: number) => dispatch({ type: 'page', page }), []);
  const clear = useCallback(() => dispatch({ type: 'clear' }), []);

  const query = useMemo<GamesQuery>(
    () => ({ ...state.values, event: settledEvent, page: state.page }),
    [state.values, state.page, settledEvent],
  );

  const isFiltered = Object.values(state.values).some(
    (value) => value !== undefined && value !== '',
  );

  return { values: state.values, query, isFiltered, setFilter, setPage, clear };
}
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/features/games/hooks/useGameFilters.test.ts`
Expected: PASS — six tests.

- [ ] **Step 5: Lint, build, commit**

```bash
yarn lint && yarn build
git add src/features/games/hooks/useGameFilters.ts src/features/games/hooks/useGameFilters.test.ts
git commit -m "Games filter and page state (#10)"
```

---

### Task 6: `useGames`

The page's one correctness argument: a superseded response must never land.

**Files:**
- Create: `apps/web/src/features/games/hooks/useGames.ts`
- Test: `apps/web/src/features/games/hooks/useGames.test.ts`

**Interfaces:**
- Consumes: `gamesPath`, `fetchGames` (Task 3); `GamePage`, `GamesQuery` (Task 3).
- Produces:

```ts
type GamesState =
  | { kind: 'loading' }
  | { kind: 'ready'; page: GamePage; refreshing: boolean }
  | { kind: 'failed'; message: string };
function useGames(query: GamesQuery): { state: GamesState; retry: () => void }
```

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/hooks/useGames.test.ts`:

```ts
import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useGames } from './useGames';
import type { GamePage, GameSummary, GamesQuery } from '../types/game';

function game(id: string, event: string): GameSummary {
  return {
    id,
    white: { playerId: 'w', name: 'White, W', rating: 2000 },
    black: { playerId: 'b', name: 'Black, B', rating: 1900 },
    event,
    site: null,
    round: null,
    playedOn: '2024-05-01',
    result: 'DRAW',
    eco: null,
    source: 'PGN_IMPORT',
  };
}

function page(games: GameSummary[]): GamePage {
  return {
    content: games,
    page: 0,
    size: 25,
    totalElements: games.length,
    totalPages: games.length === 0 ? 0 : 1,
  };
}

function jsonResponse(body: unknown): Response {
  return { ok: true, status: 200, json: async () => body } as unknown as Response;
}

/** A promise this test resolves by hand, so response ordering can be controlled. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

const NO_FILTERS: GamesQuery = { page: 0 };

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useGames', () => {
  it('loads, then reports the page', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(page([game('1', 'Hastings')]))));

    const { result } = renderHook(() => useGames(NO_FILTERS));

    expect(result.current.state.kind).toBe('loading');
    await waitFor(() => expect(result.current.state.kind).toBe('ready'));
    if (result.current.state.kind === 'ready') {
      expect(result.current.state.page.content).toHaveLength(1);
      expect(result.current.state.refreshing).toBe(false);
    }
  });

  it('requests the filters it was given', async () => {
    const fetchStub = vi.fn().mockResolvedValue(jsonResponse(page([])));
    vi.stubGlobal('fetch', fetchStub);

    renderHook(() => useGames({ result: 'DRAW', event: 'Hastings', page: 2 }));

    await waitFor(() => expect(fetchStub).toHaveBeenCalled());
    expect(fetchStub.mock.calls[0][0]).toBe('/api/games?result=DRAW&event=Hastings&page=2');
  });

  it('does not refetch when the query is a new object with the same values', async () => {
    const fetchStub = vi.fn().mockResolvedValue(jsonResponse(page([])));
    vi.stubGlobal('fetch', fetchStub);

    const { rerender } = renderHook(({ query }) => useGames(query), {
      initialProps: { query: { page: 0 } as GamesQuery },
    });
    await waitFor(() => expect(fetchStub).toHaveBeenCalledTimes(1));

    rerender({ query: { page: 0 } as GamesQuery });

    expect(fetchStub).toHaveBeenCalledTimes(1);
  });

  it('keeps the previous page on screen while the next one loads', async () => {
    const first = deferred<Response>();
    const second = deferred<Response>();
    vi.stubGlobal(
      'fetch',
      vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise),
    );

    const { result, rerender } = renderHook(({ query }) => useGames(query), {
      initialProps: { query: { page: 0 } as GamesQuery },
    });

    first.resolve(jsonResponse(page([game('1', 'Hastings')])));
    await waitFor(() => expect(result.current.state.kind).toBe('ready'));

    rerender({ query: { page: 1 } as GamesQuery });

    await waitFor(() => {
      expect(result.current.state.kind).toBe('ready');
      if (result.current.state.kind === 'ready') {
        expect(result.current.state.refreshing).toBe(true);
        expect(result.current.state.page.content[0].event).toBe('Hastings');
      }
    });

    second.resolve(jsonResponse(page([game('2', 'Wijk aan Zee')])));
    await waitFor(() => {
      if (result.current.state.kind === 'ready') {
        expect(result.current.state.refreshing).toBe(false);
        expect(result.current.state.page.content[0].event).toBe('Wijk aan Zee');
      }
    });
  });

  it('never lets a superseded response overwrite a newer one', async () => {
    // The bug this hook exists to prevent: a slow response for the filters the
    // user has already moved on from, landing last and winning.
    const slowFirst = deferred<Response>();
    const fastSecond = deferred<Response>();
    vi.stubGlobal(
      'fetch',
      vi.fn().mockReturnValueOnce(slowFirst.promise).mockReturnValueOnce(fastSecond.promise),
    );

    const { result, rerender } = renderHook(({ query }) => useGames(query), {
      initialProps: { query: { event: 'Hastings', page: 0 } as GamesQuery },
    });

    rerender({ query: { event: 'Wijk aan Zee', page: 0 } as GamesQuery });

    fastSecond.resolve(jsonResponse(page([game('2', 'Wijk aan Zee')])));
    await waitFor(() => expect(result.current.state.kind).toBe('ready'));

    slowFirst.resolve(jsonResponse(page([game('1', 'Hastings')])));
    await new Promise((resolve) => setTimeout(resolve, 0));

    if (result.current.state.kind === 'ready') {
      expect(result.current.state.page.content[0].event).toBe('Wijk aan Zee');
    } else {
      throw new Error(`expected ready, got ${result.current.state.kind}`);
    }
  });

  it('reports a failure, and retries on demand', async () => {
    const fetchStub = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse(page([game('1', 'Hastings')])));
    vi.stubGlobal('fetch', fetchStub);

    const { result } = renderHook(() => useGames(NO_FILTERS));

    await waitFor(() => expect(result.current.state.kind).toBe('failed'));
    if (result.current.state.kind === 'failed') {
      expect(result.current.state.message).toContain('Failed to fetch');
    }

    result.current.retry();

    await waitFor(() => expect(result.current.state.kind).toBe('ready'));
  });

  it('does not report an aborted request as a failure', async () => {
    const fetchStub = vi.fn().mockImplementation(
      (_path: string, init: { signal: AbortSignal }) =>
        new Promise((_resolve, reject) => {
          init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
        }),
    );
    vi.stubGlobal('fetch', fetchStub);

    const { result, unmount } = renderHook(() => useGames(NO_FILTERS));
    unmount();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(result.current.state.kind).toBe('loading');
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/hooks/useGames.test.ts`
Expected: FAIL — cannot resolve `./useGames`.

- [ ] **Step 3: Write the hook**

Create `apps/web/src/features/games/hooks/useGames.ts`:

```ts
import { useCallback, useEffect, useState } from 'react';
import { fetchGames, gamesPath } from '../api/games';
import type { GamePage, GamesQuery } from '../types/game';

export type GamesState =
  | { kind: 'loading' }
  | { kind: 'ready'; page: GamePage; refreshing: boolean }
  | { kind: 'failed'; message: string };

export interface UseGames {
  state: GamesState;
  retry: () => void;
}

/**
 * The list request as state.
 *
 * <p>The effect depends on the request *path*, not on the query object. The path
 * is the request, so it is what actually changed; and being a string, an
 * equivalent query built fresh on every render does not restart the request.
 *
 * <p>Every request carries an `AbortController`, and the cleanup aborts it. This
 * is what stops a slow response for filters the user has left behind from landing
 * last and repainting the table. An abort is not a failure — the signal is checked
 * before any state is set — so a cancelled request leaves the state alone.
 *
 * <p>A refetch keeps the current page visible and raises `refreshing` rather than
 * falling back to `loading`, so paging does not blank the table.
 */
export function useGames(query: GamesQuery): UseGames {
  const path = gamesPath(query);
  const [state, setState] = useState<GamesState>({ kind: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    setState((current) => {
      if (current.kind === 'ready') {
        return { ...current, refreshing: true };
      }
      return current.kind === 'loading' ? current : { kind: 'loading' };
    });

    fetchGames(path, controller.signal)
      .then((page) => {
        if (!controller.signal.aborted) {
          setState({ kind: 'ready', page, refreshing: false });
        }
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setState({
          kind: 'failed',
          message: error instanceof Error ? error.message : String(error),
        });
      });

    return () => controller.abort();
  }, [path, attempt]);

  const retry = useCallback(() => setAttempt((previous) => previous + 1), []);

  return { state, retry };
}
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/features/games/hooks/useGames.test.ts`
Expected: PASS — seven tests. If the superseded-response test fails, the abort guard is wrong; do not weaken the test.

- [ ] **Step 5: Lint, build, commit**

```bash
yarn lint && yarn build
git add src/features/games/hooks/useGames.ts src/features/games/hooks/useGames.test.ts
git commit -m "Games request state with abort-based staleness handling (#10)"
```

---

### Task 7: `GameRow` and `GameTable`

**Files:**
- Create: `apps/web/src/features/games/components/GameRow.tsx`
- Create: `apps/web/src/features/games/components/GameTable.tsx`
- Create: `apps/web/src/features/games/components/GameTable.module.css`
- Test: `apps/web/src/features/games/components/GameTable.test.tsx`

**Interfaces:**
- Consumes: `GameSummary` (Task 3); `resultLabel`, `sourceLabel`, `sideLabel`, `orDash`, `EM_DASH` (Task 4).
- Produces: `GameRow({ game }: { game: GameSummary })`, `GameTable({ games }: { games: GameSummary[] })`

Nine columns: White, Black, Result, Date, Event, Site, Round, ECO, Source. `GameTable` expects a non-empty list — the page owns the two empty states, because choosing between them needs to know whether a filter is set.

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/components/GameTable.test.tsx`:

```tsx
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GameTable } from './GameTable';
import type { GameSummary } from '../types/game';

const COMPLETE: GameSummary = {
  id: '1',
  white: { playerId: 'w', name: 'Carlsen, M', rating: 2839 },
  black: { playerId: 'b', name: 'Nepomniachtchi, I', rating: 2792 },
  event: 'World Championship',
  site: 'Dubai',
  round: '6',
  playedOn: '2021-12-03',
  result: 'WHITE_WON',
  eco: 'C88',
  source: 'PGN_IMPORT',
};

const SPARSE: GameSummary = {
  id: '2',
  white: { playerId: 'w', name: 'Green, G', rating: null },
  black: { playerId: 'b', name: 'Opponent, O', rating: null },
  event: null,
  site: null,
  round: null,
  playedOn: null,
  result: 'UNFINISHED',
  eco: null,
  source: 'PERSONAL',
};

describe('GameTable', () => {
  it('renders a row per game, in the order given', () => {
    render(<GameTable games={[COMPLETE, SPARSE]} />);

    const rows = screen.getAllByRole('row').slice(1); // drop the header row
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText(/Carlsen, M/)).toBeInTheDocument();
    expect(within(rows[1]).getByText(/Green, G/)).toBeInTheDocument();
  });

  it('shows each side with the rating recorded at the time', () => {
    render(<GameTable games={[COMPLETE]} />);

    expect(screen.getByText('Carlsen, M (2839)')).toBeInTheDocument();
    expect(screen.getByText('Nepomniachtchi, I (2792)')).toBeInTheDocument();
  });

  it('omits the parentheses for a side with no rating', () => {
    render(<GameTable games={[SPARSE]} />);

    expect(screen.getByText('Green, G')).toBeInTheDocument();
  });

  it('shows the result as its PGN token', () => {
    render(<GameTable games={[COMPLETE, SPARSE]} />);

    expect(screen.getByText('1-0')).toBeInTheDocument();
    expect(screen.getByText('*')).toBeInTheDocument();
  });

  it('renders absent metadata as an em dash rather than as an empty cell', () => {
    render(<GameTable games={[SPARSE]} />);

    const row = screen.getAllByRole('row')[1];
    // date, event, site, round and ECO were all absent from the document.
    expect(within(row).getAllByText('—')).toHaveLength(5);
  });

  it('shows the date exactly as the API reports it', () => {
    render(<GameTable games={[COMPLETE]} />);

    expect(screen.getByText('2021-12-03')).toBeInTheDocument();
  });

  it('humanises the source', () => {
    render(<GameTable games={[COMPLETE]} />);

    expect(screen.getByText('PGN import')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/components/GameTable.test.tsx`
Expected: FAIL — cannot resolve `./GameTable`.

- [ ] **Step 3: Write `GameRow`**

Create `apps/web/src/features/games/components/GameRow.tsx`:

```tsx
import { orDash, resultLabel, sideLabel, sourceLabel } from '../format';
import type { GameSummary } from '../types/game';

/**
 * One game as a table row.
 *
 * <p>Its own component because
 * [#11](https://github.com/guyAOgreen/Chess-App/issues/11) turns it into a link to
 * the game viewer, and that should be a change to one file.
 */
export function GameRow({ game }: { game: GameSummary }) {
  return (
    <tr>
      <td>{sideLabel(game.white)}</td>
      <td>{sideLabel(game.black)}</td>
      <td>{resultLabel(game.result)}</td>
      <td>{orDash(game.playedOn)}</td>
      <td>{orDash(game.event)}</td>
      <td>{orDash(game.site)}</td>
      <td>{orDash(game.round)}</td>
      <td>{orDash(game.eco)}</td>
      <td>{sourceLabel(game.source)}</td>
    </tr>
  );
}
```

- [ ] **Step 4: Write `GameTable` and its styles**

Create `apps/web/src/features/games/components/GameTable.tsx`:

```tsx
import { GameRow } from './GameRow';
import styles from './GameTable.module.css';
import type { GameSummary } from '../types/game';

/**
 * The list as a table. Takes a non-empty list: "no games yet" and "no games match
 * these filters" are different statements about the page's state, and telling them
 * apart needs to know whether a filter is set, which the page knows and this does
 * not.
 */
export function GameTable({ games }: { games: GameSummary[] }) {
  return (
    <table className={styles.table}>
      <thead>
        <tr>
          <th scope="col">White</th>
          <th scope="col">Black</th>
          <th scope="col">Result</th>
          <th scope="col">Date</th>
          <th scope="col">Event</th>
          <th scope="col">Site</th>
          <th scope="col">Round</th>
          <th scope="col">ECO</th>
          <th scope="col">Source</th>
        </tr>
      </thead>
      <tbody>
        {games.map((game) => (
          <GameRow key={game.id} game={game} />
        ))}
      </tbody>
    </table>
  );
}
```

Create `apps/web/src/features/games/components/GameTable.module.css`:

```css
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 15px;
  text-align: left;
  white-space: nowrap;
}

.table th,
.table td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
}

.table th {
  color: var(--text-h);
  font-weight: 500;
}

.table tbody tr:hover {
  background: var(--accent-bg);
}
```

- [ ] **Step 5: Run the test**

Run: `yarn test src/features/games/components/GameTable.test.tsx`
Expected: PASS — seven tests.

- [ ] **Step 6: Lint, build, commit**

```bash
yarn lint && yarn build
git add src/features/games/components/GameRow.tsx src/features/games/components/GameTable.tsx src/features/games/components/GameTable.module.css src/features/games/components/GameTable.test.tsx
git commit -m "Games table and row (#10)"
```

---

### Task 8: `GameFilters`

**Files:**
- Create: `apps/web/src/features/games/components/GameFilters.tsx`
- Create: `apps/web/src/features/games/components/GameFilters.module.css`
- Test: `apps/web/src/features/games/components/GameFilters.test.tsx`

**Interfaces:**
- Consumes: `GameFilterValues`, `GameResult`, `GAME_RESULTS` (Task 3); `resultLabel` (Task 4).
- Produces:

```ts
interface GameFiltersProps {
  values: GameFilterValues;
  onChange: <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => void;
  onClear: () => void;
}
function GameFilters(props: GameFiltersProps)
```

`onChange` matches `useGameFilters`'s `setFilter` exactly, so the page passes it straight through.

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/components/GameFilters.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { GameFilters } from './GameFilters';

describe('GameFilters', () => {
  it('raises the chosen result', async () => {
    const onChange = vi.fn();
    render(<GameFilters values={{}} onChange={onChange} onClear={vi.fn()} />);

    await userEvent.selectOptions(screen.getByLabelText(/result/i), 'DRAW');

    expect(onChange).toHaveBeenCalledWith('result', 'DRAW');
  });

  it('raises undefined, not an empty string, when the result is set back to any', async () => {
    const onChange = vi.fn();
    render(<GameFilters values={{ result: 'DRAW' }} onChange={onChange} onClear={vi.fn()} />);

    await userEvent.selectOptions(screen.getByLabelText(/result/i), '');

    expect(onChange).toHaveBeenCalledWith('result', undefined);
  });

  it('raises the event term as it is typed', async () => {
    const onChange = vi.fn();
    render(<GameFilters values={{}} onChange={onChange} onClear={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/event/i), 'H');

    expect(onChange).toHaveBeenCalledWith('event', 'H');
  });

  it('raises undefined when the event term is emptied', async () => {
    const onChange = vi.fn();
    render(<GameFilters values={{ event: 'H' }} onChange={onChange} onClear={vi.fn()} />);

    await userEvent.clear(screen.getByLabelText(/event/i));

    expect(onChange).toHaveBeenCalledWith('event', undefined);
  });

  it('limits the event term to what the API accepts', () => {
    // GameListParams declares @Size(max = 255); a longer term is a 400 the form
    // can simply not produce.
    render(<GameFilters values={{}} onChange={vi.fn()} onClear={vi.fn()} />);

    expect(screen.getByLabelText(/event/i)).toHaveAttribute('maxlength', '255');
  });

  it('stops the date range being ordered backwards', () => {
    // from <= to, so from is the earliest the to input allows, and to is the
    // latest the from input allows.
    render(
      <GameFilters
        values={{ from: '2024-01-01', to: '2024-12-31' }}
        onChange={vi.fn()}
        onClear={vi.fn()}
      />,
    );

    expect(screen.getByLabelText(/from/i)).toHaveAttribute('max', '2024-12-31');
    expect(screen.getByLabelText(/to/i)).toHaveAttribute('min', '2024-01-01');
  });

  it('raises a clear', async () => {
    const onClear = vi.fn();
    render(<GameFilters values={{ result: 'DRAW' }} onChange={vi.fn()} onClear={onClear} />);

    await userEvent.click(screen.getByRole('button', { name: /clear/i }));

    expect(onClear).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/components/GameFilters.test.tsx`
Expected: FAIL — cannot resolve `./GameFilters`.

- [ ] **Step 3: Write the component**

Create `apps/web/src/features/games/components/GameFilters.tsx`:

```tsx
import styles from './GameFilters.module.css';
import { resultLabel } from '../format';
import { GAME_RESULTS, type GameFilterValues, type GameResult } from '../types/game';

/** The API treats blank and absent as the same thing, and so does the state: an
 * emptied control raises `undefined` rather than `''`. */
function orUndefined(value: string): string | undefined {
  return value === '' ? undefined : value;
}

export interface GameFiltersProps {
  values: GameFilterValues;
  onChange: <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => void;
  onClear: () => void;
}

/**
 * The filter controls. Every one of them can be operated: the endpoint's
 * `playerId` filter takes a UUID that nothing yet turns a name into, so it waits
 * for [#21](https://github.com/guyAOgreen/Chess-App/issues/21) rather than shipping
 * as a control nobody can use.
 *
 * <p>Submission is prevented because there is nothing to submit — every change
 * applies as it is made.
 */
export function GameFilters({ values, onChange, onClear }: GameFiltersProps) {
  return (
    <form className={styles.filters} onSubmit={(event) => event.preventDefault()}>
      <label className={styles.field}>
        Result
        <select
          value={values.result ?? ''}
          onChange={(event) =>
            onChange('result', orUndefined(event.target.value) as GameResult | undefined)
          }
        >
          <option value="">Any</option>
          {GAME_RESULTS.map((result) => (
            <option key={result} value={result}>
              {resultLabel(result)}
            </option>
          ))}
        </select>
      </label>

      <label className={styles.field}>
        From
        <input
          type="date"
          value={values.from ?? ''}
          max={values.to}
          onChange={(event) => onChange('from', orUndefined(event.target.value))}
        />
      </label>

      <label className={styles.field}>
        To
        <input
          type="date"
          value={values.to ?? ''}
          min={values.from}
          onChange={(event) => onChange('to', orUndefined(event.target.value))}
        />
      </label>

      <label className={styles.field}>
        Event
        <input
          type="search"
          maxLength={255}
          placeholder="Any event"
          value={values.event ?? ''}
          onChange={(event) => onChange('event', orUndefined(event.target.value))}
        />
      </label>

      <button type="button" className={styles.clear} onClick={onClear}>
        Clear filters
      </button>
    </form>
  );
}
```

Create `apps/web/src/features/games/components/GameFilters.module.css`:

```css
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 20px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
  color: var(--text);
}

.field select,
.field input {
  font: inherit;
  font-size: 15px;
  color: var(--text-h);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 6px 8px;
}

.field select:focus-visible,
.field input:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.clear {
  font: inherit;
  font-size: 15px;
  color: var(--text-h);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 7px 12px;
  cursor: pointer;
}

.clear:hover {
  border-color: var(--accent-border);
}
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/features/games/components/GameFilters.test.tsx`
Expected: PASS — seven tests.

- [ ] **Step 5: Lint, build, commit**

```bash
yarn lint && yarn build
git add src/features/games/components/GameFilters.tsx src/features/games/components/GameFilters.module.css src/features/games/components/GameFilters.test.tsx
git commit -m "Games filter controls (#10)"
```

---

### Task 9: `GamePager`

**Files:**
- Create: `apps/web/src/features/games/components/GamePager.tsx`
- Create: `apps/web/src/features/games/components/GamePager.module.css`
- Test: `apps/web/src/features/games/components/GamePager.test.tsx`

**Interfaces:**
- Consumes: nothing beyond React.
- Produces:

```ts
interface GamePagerProps {
  page: number;          // zero-based, as the API reports it
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}
function GamePager(props: GamePagerProps)
```

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/components/GamePager.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { GamePager } from './GamePager';

describe('GamePager', () => {
  it('counts pages from one, because the API counts them from zero', () => {
    render(<GamePager page={1} totalElements={168} totalPages={7} onPageChange={vi.fn()} />);

    expect(screen.getByText(/page 2 of 7/i)).toBeInTheDocument();
    expect(screen.getByText(/168 games/i)).toBeInTheDocument();
  });

  it('cannot go back from the first page', () => {
    render(<GamePager page={0} totalElements={168} totalPages={7} onPageChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled();
  });

  it('cannot go forward from the last page', () => {
    render(<GamePager page={6} totalElements={168} totalPages={7} onPageChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: /previous/i })).toBeEnabled();
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
  });

  it('moves a page at a time', async () => {
    const onPageChange = vi.fn();
    render(<GamePager page={3} totalElements={168} totalPages={7} onPageChange={onPageChange} />);

    await userEvent.click(screen.getByRole('button', { name: /next/i }));
    expect(onPageChange).toHaveBeenCalledWith(4);

    await userEvent.click(screen.getByRole('button', { name: /previous/i }));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it('says "1 game" rather than "1 games"', () => {
    render(<GamePager page={0} totalElements={1} totalPages={1} onPageChange={vi.fn()} />);

    expect(screen.getByText(/1 game\b/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/components/GamePager.test.tsx`
Expected: FAIL — cannot resolve `./GamePager`.

- [ ] **Step 3: Write the component**

Create `apps/web/src/features/games/components/GamePager.tsx`:

```tsx
import styles from './GamePager.module.css';

export interface GamePagerProps {
  /** Zero-based, as `GamePageResponse` reports it. */
  page: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

/**
 * Previous and Next, and where the user is.
 *
 * <p>The envelope carries `totalPages` so a numbered pager is possible, and
 * nothing asks for one: a personal database is a handful of pages, and the data to
 * build one is already here the day it is wanted.
 */
export function GamePager({ page, totalElements, totalPages, onPageChange }: GamePagerProps) {
  const games = `${totalElements} ${totalElements === 1 ? 'game' : 'games'}`;

  return (
    <nav className={styles.pager} aria-label="Pagination">
      <button
        type="button"
        className={styles.step}
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        Previous
      </button>
      <span className={styles.position}>
        Page {page + 1} of {totalPages} · {games}
      </span>
      <button
        type="button"
        className={styles.step}
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </button>
    </nav>
  );
}
```

Create `apps/web/src/features/games/components/GamePager.module.css`:

```css
.pager {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
}

.step {
  font: inherit;
  font-size: 15px;
  color: var(--text-h);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 6px 12px;
  cursor: pointer;
}

.step:hover:not(:disabled) {
  border-color: var(--accent-border);
}

.step:disabled {
  opacity: 0.5;
  cursor: default;
}

.position {
  font-size: 14px;
}
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/features/games/components/GamePager.test.tsx`
Expected: PASS — five tests.

- [ ] **Step 5: Lint, build, commit**

```bash
yarn lint && yarn build
git add src/features/games/components/GamePager.tsx src/features/games/components/GamePager.module.css src/features/games/components/GamePager.test.tsx
git commit -m "Games pager (#10)"
```

---

### Task 10: `GamesPage`, the app shell, and the stylesheet

The composition, and the only task that changes files outside the feature.

**Files:**
- Create: `apps/web/src/features/games/pages/GamesPage.tsx`
- Create: `apps/web/src/features/games/pages/GamesPage.module.css`
- Test: `apps/web/src/features/games/pages/GamesPage.test.tsx`
- Modify: `apps/web/src/app/App.tsx`
- Modify: `apps/web/src/app/App.test.tsx`
- Modify: `apps/web/src/index.css:53-63` (the `#root` block)

**Interfaces:**
- Consumes: `useGameFilters` (Task 5), `useGames` (Task 6), `GameFilters` (Task 8), `GameTable` (Task 7), `GamePager` (Task 9).
- Produces: `GamesPage()` — no props.

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/pages/GamesPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GamesPage } from './GamesPage';
import type { GamePage, GameSummary } from '../types/game';

function game(id: string, event: string): GameSummary {
  return {
    id,
    white: { playerId: 'w', name: 'Carlsen, M', rating: 2839 },
    black: { playerId: 'b', name: 'Nepomniachtchi, I', rating: 2792 },
    event,
    site: 'Dubai',
    round: '6',
    playedOn: '2021-12-03',
    result: 'WHITE_WON',
    eco: 'C88',
    source: 'PGN_IMPORT',
  };
}

function page(games: GameSummary[]): GamePage {
  return {
    content: games,
    page: 0,
    size: 25,
    totalElements: games.length,
    totalPages: games.length === 0 ? 0 : 1,
  };
}

function respondWith(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as unknown as Response;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('GamesPage', () => {
  it('lists the games it loaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respondWith(page([game('1', 'Hastings')]))));

    render(<GamesPage />);

    expect(screen.getByText(/loading games/i)).toBeInTheDocument();
    expect(await screen.findByText('Hastings')).toBeInTheDocument();
    expect(screen.getByText('Carlsen, M (2839)')).toBeInTheDocument();
  });

  it('sends the event term once typing settles, asking for the first page', async () => {
    const fetchStub = vi.fn().mockResolvedValue(respondWith(page([game('1', 'Hastings')])));
    vi.stubGlobal('fetch', fetchStub);

    render(<GamesPage />);
    await screen.findByText('Hastings');

    await userEvent.type(screen.getByLabelText(/event/i), 'Hast');

    await waitFor(() => {
      const paths = fetchStub.mock.calls.map((call) => call[0] as string);
      expect(paths.some((path) => path.includes('event=Hast') && path.includes('page=0'))).toBe(
        true,
      );
    });
  });

  it('says the database is empty when nothing is filtered', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respondWith(page([]))));

    render(<GamesPage />);

    expect(await screen.findByText(/no games yet/i)).toBeInTheDocument();
  });

  it('says the filters matched nothing when a filter is set', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respondWith(page([]))));

    render(<GamesPage />);
    await screen.findByText(/no games yet/i);

    await userEvent.selectOptions(screen.getByLabelText(/result/i), 'DRAW');

    expect(await screen.findByText(/no games match these filters/i)).toBeInTheDocument();
  });

  it('keeps the filters usable when the request fails, and retries', async () => {
    const fetchStub = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(respondWith(page([game('1', 'Hastings')])));
    vi.stubGlobal('fetch', fetchStub);

    render(<GamesPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/failed to fetch/i);
    expect(screen.getByLabelText(/event/i)).toBeEnabled();

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByText('Hastings')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/pages/GamesPage.test.tsx`
Expected: FAIL — cannot resolve `./GamesPage`.

- [ ] **Step 3: Write the page**

Create `apps/web/src/features/games/pages/GamesPage.tsx`:

```tsx
import styles from './GamesPage.module.css';
import { GameFilters } from '../components/GameFilters';
import { GamePager } from '../components/GamePager';
import { GameTable } from '../components/GameTable';
import { useGameFilters } from '../hooks/useGameFilters';
import { useGames } from '../hooks/useGames';

/**
 * Composes the two hooks and the three components, and owns the states that are
 * about the page rather than about a table: nothing loaded yet, nothing matched,
 * nothing arrived.
 *
 * <p>The filter form is rendered in every state. A user who filters into an empty
 * result has to be able to filter back out, and a user whose request failed has to
 * be able to change it.
 */
export function GamesPage() {
  const { values, query, isFiltered, setFilter, setPage, clear } = useGameFilters();
  const { state, retry } = useGames(query);

  return (
    <section className={styles.page}>
      <h2>Games</h2>

      <GameFilters values={values} onChange={setFilter} onClear={clear} />

      {state.kind === 'loading' && <p role="status">Loading games…</p>}

      {state.kind === 'failed' && (
        <div role="alert" className={styles.failure}>
          <p>{state.message}</p>
          <button type="button" onClick={retry}>
            Retry
          </button>
        </div>
      )}

      {state.kind === 'ready' &&
        (state.page.content.length === 0 ? (
          <p role="status">
            {isFiltered
              ? 'No games match these filters.'
              : 'No games yet. Import a PGN to add one.'}
          </p>
        ) : (
          <>
            <div className={styles.tableWrapper} aria-busy={state.refreshing}>
              <GameTable games={state.page.content} />
            </div>
            <GamePager
              page={state.page.page}
              totalElements={state.page.totalElements}
              totalPages={state.page.totalPages}
              onPageChange={setPage}
            />
          </>
        ))}
    </section>
  );
}
```

Create `apps/web/src/features/games/pages/GamesPage.module.css`:

```css
.page {
  text-align: left;
  padding: 24px 0;
}

/* Nine columns do not fit a narrow screen. Scrolling the table is honest;
   inventing a card layout for a screen nobody has used is guesswork. */
.tableWrapper {
  overflow-x: auto;
}

.tableWrapper[aria-busy='true'] {
  opacity: 0.6;
}

.failure {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--accent-border);
  border-radius: 4px;
  background: var(--accent-bg);
}

.failure button {
  font: inherit;
  font-size: 15px;
  color: var(--text-h);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 6px 12px;
  cursor: pointer;
}
```

- [ ] **Step 4: Run the page test**

Run: `yarn test src/features/games/pages/GamesPage.test.tsx`
Expected: PASS — five tests.

- [ ] **Step 5: Mount the page and fix the shell**

In `apps/web/src/app/App.tsx`, replace the whole file:

```tsx
import { BackendHealthCard } from '../features/system-health/components/BackendHealthCard';
import { useBackendHealth } from '../features/system-health/hooks/useBackendHealth';
import { GamesPage } from '../features/games/pages/GamesPage';

export default function App() {
  const health = useBackendHealth();

  return (
    <main>
      <h1>Chess Prep</h1>
      <GamesPage />
      <BackendHealthCard state={health} />
    </main>
  );
}
```

In `apps/web/src/index.css`, replace the `#root` block. A table cannot live inside `text-align: center`, and nine columns want more than 1126px:

```css
#root {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 24px;
  border-inline: 1px solid var(--border);
  min-height: 100svh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
```

- [ ] **Step 6: Stub `fetch` in the app test**

`App` now fetches twice on mount. In `apps/web/src/app/App.test.tsx`, replace the whole file:

```tsx
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('App', () => {
  it('renders the application name', () => {
    // The app fetches health and games on mount; neither is what this asserts.
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    render(<App />);

    expect(screen.getByRole('heading', { name: /chess prep/i })).toBeInTheDocument();
  });
});
```

- [ ] **Step 7: Run everything**

Run: `yarn test && yarn lint && yarn build`
Expected: PASS — every suite green, no lint errors, build succeeds.

- [ ] **Step 8: Look at it**

Run: `yarn dev` with the backend running (`docker compose up -d` in `infra/`, then the core service).
Check: the table is left-aligned and readable; filtering by event does not fire a request per keystroke; the date inputs refuse a backwards range; paging keeps the rows on screen instead of blanking them; the health card still renders below.

- [ ] **Step 9: Commit**

```bash
git add src/features/games/pages src/app/App.tsx src/app/App.test.tsx src/index.css
git commit -m "Games list page, mounted in the app shell (#10)"
```

---

## Verification

After Task 10, from `apps/web`:

```bash
yarn test    # every suite
yarn lint    # oxlint
yarn build   # tsc -b && vite build
```

Then, against a running backend, the manual check in Task 10 Step 8.

## What This Plan Does Not Build

Straight from the spec's out-of-scope list, so an implementer does not add them:
player and colour filters (#21) · routing and a row that opens a game (#11) · the game viewer (#11) · generated API types (#27) · sortable columns · filtering by source, site, round, ECO or rating · a PGN import screen · field-level rejection messages (#43) · authentication (#25) · a shared component library.
