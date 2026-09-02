# Game Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replay a stored game — a chessboard, a move list, and click-a-move navigation — reachable at `/games/:id` from the games list.

**Architecture:** One pure module, `replay.ts`, turns SAN movetext into a position per ply using chess.js; it is the only file in the app that imports chess.js. Everything else is presentational and takes plain data: `Chessboard` takes a FEN string, `MoveList` takes plies and an index. `react-router` introduces `/` and `/games/:id`. Nothing but the page knows a fetch exists, which is the contract #17 reuses.

**Tech Stack:** React 19, TypeScript ~6.0, Vite 8, Vitest 4 + React Testing Library, CSS Modules, Yarn classic. Two new dependencies: `chess.js@^1.4.0` (BSD-2-Clause) and `react-router@^8` (MIT). All commands run from `apps/web`.

**Spec:** [`docs/superpowers/specs/2026-09-01-game-viewer-design.md`](../specs/2026-09-01-game-viewer-design.md)

**Issue:** [#11](https://github.com/guyAOgreen/Chess-App/issues/11) — M1, Game database

## Global Constraints

- **Exactly two new dependencies**, both runtime: `chess.js` and `react-router`. Nothing else is added to `package.json`.
- **`chess.js` is imported only by `src/features/games/replay.ts`.** No other file in `apps/web` may import it. This is ADR 0001's pattern applied on the frontend.
- **`replay` never throws.** It returns `{ plies, error }`. Both `loadPgn` and `move()` in chess.js raise on failure, so every call is inside a `try`.
- **The backend does not change.** `GET /api/games/{id}` already exists.
- **Stored movetext is normalised SAN with move numbers** — `ValidatedMoves.of` regenerates it via `MoveList.toSanWithMoveNumbers()`, so it carries no tag pairs, no terminal result token, and no comments, variations or NAGs.
- **CSS Modules beside each component**, using only the custom properties in `src/index.css`. Two new board tokens are added there in Task 5; no colour literals anywhere else.
- **`yarn lint` must stay warning-free.** The repo currently has zero warnings, and one permanent warning trains people to skim past warnings.
- **No Javadoc `<p>` tags in TSDoc comments.** Use a blank comment line for a paragraph break.
- **TDD.** The failing test comes first, you run it, and you watch it fail before writing implementation.
- **Every task ends with `yarn test`, `yarn lint` and `yarn build` green, then a commit.** Commit message: sentence-case subject, blank line, short body. It must end with exactly:
  `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`
- **Do not kill processes.** No `taskkill`, no killing `node.exe`. An agent doing that on an earlier issue killed every Node process on the machine.

### A house rule this project enforces

**A test that cannot fail is a defect**, and specs in this repository have shipped one in nearly every task. For each test you write, mutate the behaviour it names and confirm the test fails. Traps already found in this codebase:

- `toMatchObject` is a *subset* check — it passes on a present-but-undefined value, and is entirely vacuous on an `AbortSignal`, which has no own enumerable properties.
- Asserting a function against the constant it is testing (`expect(orDash(null)).toBe(EM_DASH)`) proves nothing.
- A count-based assertion passes with the right number of values in the wrong places — an em-dash count of 5 survived swapping two columns.
- An assertion reading part of an object lets a mutant delete the rest.
- An unmount-based assertion passes because React discards the update, not because the code is right.

**On the expected FEN strings in this plan:** they are written by hand. If one is wrong, the test will fail — **fix the FEN by reasoning about the position, and say so in your report. Never weaken an assertion to make it pass.**

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `src/features/games/replay.ts` | PURE. movetext → `Replayed`. The only chess.js importer. |
| `src/features/games/hooks/useGame.ts` | One game's request state: loading / ready / invalid-id / not-found / failed. |
| `src/features/games/hooks/useReplay.ts` | Current ply index and selection. Takes plies, never movetext. |
| `src/features/games/components/Chessboard.tsx` | FEN → 8×8 grid. No chess.js. |
| `src/features/games/components/MoveList.tsx` | Plies, current index, callback. |
| `src/features/games/components/GameHeader.tsx` | Players, event, date, result, ECO, source. |
| `src/features/games/pages/GameViewerPage.tsx` | Composes the hooks and the three components. |
| `src/app/HomePage.tsx` | The `/` route: games list + health card. |
| `public/pieces/*.svg` + `LICENCE` | Twelve Cburnett SVGs under the 3-clause BSD option. |

**Modified:**

| File | Change |
|---|---|
| `src/features/games/types/game.ts` | `+ Game`, `+ Ply` |
| `src/features/games/api/games.ts` | `+ gamePath`, `+ fetchGame`, `+ GameNotFound` |
| `src/features/games/components/GameRow.tsx` | `+` a View cell containing a `<Link>` |
| `src/features/games/components/GameTable.tsx` | `+` the matching `<th scope="col">` |
| `src/features/games/components/GameTable.test.tsx` | nine headers → ten |
| `src/app/App.tsx` | becomes the router |
| `src/app/App.test.tsx` | routing assertions |
| `src/index.css` | `+` two board colour tokens |

---

### Task 1: The detail contract — types and API module

**Files:**
- Modify: `apps/web/src/features/games/types/game.ts` (append)
- Modify: `apps/web/src/features/games/api/games.ts` (append)
- Test: `apps/web/src/features/games/api/games.test.ts` (extend)

**Interfaces:**
- Consumes: `getJson`, `queryString` from `src/lib/api.ts`; `GameSummary` from `types/game.ts`.
- Produces:
  - `interface Game extends GameSummary { movetext: string }`
  - `interface Ply { index: number; moveNumber: number; colour: 'white' | 'black' | null; san: string | null; fen: string }`
  - `gamePath(id: string): string`
  - `fetchGame(path: string, signal?: AbortSignal): Promise<Game>`
  - `class GameNotFound extends Error`

- [ ] **Step 1: Write the failing tests**

Append to `apps/web/src/features/games/api/games.test.ts`. It already has `jsonResponse(body, status)` and `nonJsonResponse(status)` helpers and an `afterEach` calling `vi.unstubAllGlobals()` — reuse them; do not redeclare them.

```ts
import { fetchGame, gamePath, GameNotFound } from './games';
import type { Game } from '../types/game';

const A_GAME: Game = {
  id: '11111111-1111-1111-1111-111111111111',
  white: { playerId: 'w', name: 'Carlsen, M', rating: 2839 },
  black: { playerId: 'b', name: 'Nepomniachtchi, I', rating: 2792 },
  event: 'World Championship',
  site: 'Dubai',
  round: '6',
  playedOn: '2021-12-03',
  result: 'WHITE_WON',
  eco: 'C88',
  source: 'PGN_IMPORT',
  movetext: '1. e4 e5',
};

describe('gamePath', () => {
  it('addresses one game by identifier', () => {
    expect(gamePath('11111111-1111-1111-1111-111111111111')).toBe(
      '/api/games/11111111-1111-1111-1111-111111111111',
    );
  });

  it('encodes an identifier that would otherwise change the path', () => {
    expect(gamePath('a/b')).toBe('/api/games/a%2Fb');
  });
});

describe('fetchGame', () => {
  it('returns the game, movetext included', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    await expect(fetchGame('/api/games/x')).resolves.toEqual(A_GAME);
  });

  it('distinguishes a missing game from a failure, because they mean different things', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 404 }, 404)));

    await expect(fetchGame('/api/games/x')).rejects.toBeInstanceOf(GameNotFound);
  });

  it('treats any other rejection as a failure, naming the status', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 400 }, 400)));

    const failure = fetchGame('/api/games/x');
    await expect(failure).rejects.not.toBeInstanceOf(GameNotFound);
    await expect(failure).rejects.toThrow(/\(400\)/);
  });

  it('fails when the response is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(nonJsonResponse(502)));

    await expect(fetchGame('/api/games/x')).rejects.toThrow(/502/);
  });

  it('fails, keeping the transport message, when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(fetchGame('/api/games/x')).rejects.toThrow(/Failed to fetch/);
  });

  it('passes the abort signal through', async () => {
    const fetchStub = vi.fn().mockResolvedValue(jsonResponse(A_GAME));
    vi.stubGlobal('fetch', fetchStub);
    const controller = new AbortController();

    await fetchGame('/api/games/x', controller.signal);

    expect(fetchStub.mock.calls[0][1].signal).toBe(controller.signal);
  });
});
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `yarn test src/features/games/api/games.test.ts`
Expected: FAIL — `gamePath`, `fetchGame` and `GameNotFound` are not exported.

- [ ] **Step 3: Add the types**

Append to `apps/web/src/features/games/types/game.ts`:

```ts
/**
 * The detail representation, returned by `GET /api/games/{id}`: a summary plus
 * the moves. Mirrors the backend's `GameResponse`, which is `GameSummaryResponse`
 * with `movetext` added.
 *
 * `movetext` is normalised SAN with move numbers — the backend regenerates it
 * from the parsed move list rather than storing what was submitted, so it carries
 * no tag pairs, no terminal result token, and no comments, variations or NAGs.
 */
export interface Game extends GameSummary {
  movetext: string;
}

/**
 * One position in a replayed game.
 *
 * Index 0 is the initial position and is always present, which is why the
 * remaining fields are nullable. Without it there is no honest value for "which
 * ply is selected" before the first move, and every consumer needs a branch for
 * it.
 */
export interface Ply {
  index: number;
  /** 1-based; 0 for the initial position. */
  moveNumber: number;
  colour: 'white' | 'black' | null;
  san: string | null;
  fen: string;
}
```

- [ ] **Step 4: Add the API functions**

Append to `apps/web/src/features/games/api/games.ts`, and add `Game` to the existing type-only import from `../types/game`:

```ts
/**
 * A game that is not here. Distinct from `GamesRequestFailed` because the backend
 * went to trouble to distinguish them: #9 answers 404 for an identifier that
 * parses but matches no game, and 400 for one that does not parse. Only one of
 * those is worth offering a Retry for.
 */
export class GameNotFound extends Error {}

export function gamePath(id: string): string {
  return `/api/games/${encodeURIComponent(id)}`;
}

export async function fetchGame(path: string, signal?: AbortSignal): Promise<Game> {
  const response = await getJson<Game>(path, { signal });

  switch (response.kind) {
    case 'body':
      if (response.ok) {
        return response.data;
      }
      if (response.status === 404) {
        throw new GameNotFound('No game with that identifier.');
      }
      throw new GamesRequestFailed(`The server rejected the request (${response.status}).`);
    case 'invalid-body':
      throw new GamesRequestFailed(
        `The server answered ${response.status} with something that is not JSON.`,
      );
    case 'unreachable':
      throw new GamesRequestFailed(`Could not reach the server (${response.message}).`);
  }
}
```

- [ ] **Step 5: Run the tests**

Run: `yarn test src/features/games/api/games.test.ts`
Expected: PASS — the six new tests plus the existing ones.

- [ ] **Step 6: Mutation-check**

Change the `response.status === 404` branch to `response.status === 410`, run the suite, and confirm the "distinguishes a missing game" test fails. Restore it.

- [ ] **Step 7: Lint, build, commit**

```bash
yarn test && yarn lint && yarn build
git add src/features/games/types/game.ts src/features/games/api/games.ts src/features/games/api/games.test.ts
git commit -m "Game detail contract and API module (#11)"
```

---

### Task 2: `replay` — movetext to positions

The feature's hardest part, and a pure function. No React, no DOM, no network.

**Files:**
- Create: `apps/web/src/features/games/replay.ts`
- Test: `apps/web/src/features/games/replay.test.ts`
- Modify: `apps/web/package.json` (add `chess.js`)

**Interfaces:**
- Consumes: `Ply` from `../types/game` (Task 1); `messageOf` from `src/lib/api.ts`.
- Produces:
  - `interface Replayed { plies: Ply[]; error: string | null }`
  - `replay(movetext: string): Replayed`
  - `const INITIAL_FEN: string`

- [ ] **Step 1: Add the dependency**

Run: `yarn add chess.js@^1.4.0`
Then confirm nothing else was added: `git diff package.json` should show one line.

- [ ] **Step 2: Write the failing test**

Create `apps/web/src/features/games/replay.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { INITIAL_FEN, replay } from './replay';

/** The placement field — the part of a FEN before the first space. */
function placement(fen: string): string {
  return fen.split(' ')[0];
}

describe('replay', () => {
  it('yields the initial position for empty movetext', () => {
    const { plies, error } = replay('');

    expect(error).toBeNull();
    expect(plies).toHaveLength(1);
    expect(plies[0]).toEqual({
      index: 0,
      moveNumber: 0,
      colour: null,
      san: null,
      fen: INITIAL_FEN,
    });
  });

  it('always begins at the initial position', () => {
    const { plies } = replay('1. e4 e5');

    expect(plies[0].san).toBeNull();
    expect(plies[0].fen).toBe(INITIAL_FEN);
  });

  it('produces the position after each ply', () => {
    const { plies, error } = replay('1. e4 e5 2. Nf3');

    expect(error).toBeNull();
    expect(plies).toHaveLength(4);
    expect(plies[1].fen).toBe('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1');
    expect(plies.map((p) => p.san)).toEqual([null, 'e4', 'e5', 'Nf3']);
  });

  it('numbers moves and alternates colours', () => {
    const { plies } = replay('1. e4 e5 2. Nf3 Nc6');

    expect(plies.map((p) => p.moveNumber)).toEqual([0, 1, 1, 2, 2]);
    expect(plies.map((p) => p.colour)).toEqual([null, 'white', 'black', 'white', 'black']);
    expect(plies.map((p) => p.index)).toEqual([0, 1, 2, 3, 4]);
  });

  it('replays castling', () => {
    // 5. O-O puts the white king on g1 and the h1 rook on f1.
    const { plies, error } = replay('1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O');

    expect(error).toBeNull();
    expect(placement(plies.at(-1)!.fen)).toMatch(/RNBQ1RK1$/);
  });

  it('replays en passant', () => {
    const { plies, error } = replay('1. e4 d5 2. e5 f5 3. exf6');

    expect(error).toBeNull();
    expect(plies.at(-1)!.san).toBe('exf6');
    // The captured black f5 pawn is gone: rank 5 holds only the black d-pawn.
    expect(placement(plies.at(-1)!.fen).split('/')[3]).toBe('3p4');
  });

  it('replays promotion', () => {
    const { plies, error } = replay('1. e4 d5 2. exd5 c6 3. dxc6 Nf6 4. cxb7 Bd7 5. bxa8=Q');

    expect(error).toBeNull();
    expect(plies.at(-1)!.san).toBe('bxa8=Q');
    // a8 is now a white queen; c8 and g8 emptied by Bd7 and Nf6.
    expect(placement(plies.at(-1)!.fen).split('/')[0]).toBe('Qn1qkb1r');
  });

  it('replays a disambiguated move', () => {
    // Both black knights can reach d7, so SAN must name the file.
    const { plies, error } = replay('1. d4 Nf6 2. c4 e6 3. Nc3 d5 4. Nf3 Nbd7');

    expect(error).toBeNull();
    expect(plies.at(-1)!.san).toBe('Nbd7');
  });

  it('reports unparseable movetext instead of throwing', () => {
    const { plies, error } = replay('1. e4 e5 2. Qxf7');

    expect(error).not.toBeNull();
    expect(plies).toHaveLength(1);
    expect(plies[0].fen).toBe(INITIAL_FEN);
  });

  it('handles movetext with no terminal token, which is what the API returns', () => {
    // ValidatedMoves strips the result token, so nothing stored ends in 1-0.
    const { error } = replay('1. e4 e5');

    expect(error).toBeNull();
  });
});
```

- [ ] **Step 3: Run the test and watch it fail**

Run: `yarn test src/features/games/replay.test.ts`
Expected: FAIL — cannot resolve `./replay`.

- [ ] **Step 4: Write the module**

Create `apps/web/src/features/games/replay.ts`:

```ts
import { Chess } from 'chess.js';
import { messageOf } from '../../lib/api';
import type { Ply } from './types/game';

/** The standard starting position. Stored games carry no FEN tag, so every game
 * begins here. */
export const INITIAL_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

const INITIAL_PLY: Ply = {
  index: 0,
  moveNumber: 0,
  colour: null,
  san: null,
  fen: INITIAL_FEN,
};

export interface Replayed {
  /** Never empty: index 0 is always the initial position. */
  plies: Ply[];
  /** Set when the movetext could not be replayed at all. */
  error: string | null;
}

/**
 * The positions a game passes through.
 *
 * The only file in this application that imports chess.js — ADR 0001's pattern,
 * which wraps chesslib behind one boundary on the backend for the same reasons:
 * the constraint is enforced in one place, and the fallback stays real.
 *
 * Parsing goes through `loadPgn` rather than splitting SAN tokens by hand.
 * Movetext is a grammar, not a whitespace-delimited list — move numbers may sit
 * against their move, and SAN carries disambiguation, captures, promotion,
 * castling and check markers.
 *
 * Strict mode is deliberate. The input is machine-generated canonical SAN, so
 * anything chess.js considers non-strict is a divergence between it and chesslib,
 * and should surface as a visible error rather than a quietly wrong board.
 *
 * The trailing ` *` is appended to a local copy only. The backend needs exactly
 * this for chesslib — `ChesslibPgnParser` documents that chesslib parses movetext
 * only when the text ends in a result token — and it is harmless here whether or
 * not chess.js has the same requirement.
 *
 * Never throws: both `loadPgn` and `move()` raise on failure, and a viewer that
 * blanked on a bad game would be worse than one that says what went wrong.
 */
export function replay(movetext: string): Replayed {
  if (movetext.trim() === '') {
    return { plies: [INITIAL_PLY], error: null };
  }

  const chess = new Chess();
  try {
    chess.loadPgn(`${movetext} *`, { strict: true });
  } catch (error: unknown) {
    return { plies: [INITIAL_PLY], error: messageOf(error) };
  }

  const moves = chess.history({ verbose: true });
  const plies: Ply[] = moves.map((move, i) => ({
    index: i + 1,
    moveNumber: Math.floor(i / 2) + 1,
    colour: i % 2 === 0 ? 'white' : 'black',
    san: move.san,
    fen: move.after,
  }));

  return { plies: [INITIAL_PLY, ...plies], error: null };
}
```

**Two things to confirm against the installed version rather than assume:** that `loadPgn`'s options argument is `{ strict: true }`, and that a verbose history move exposes `after` as the FEN following the move. If either differs, adjust and record it in your report. Both are covered by the tests above, so a wrong guess fails loudly.

- [ ] **Step 5: Run the test**

Run: `yarn test src/features/games/replay.test.ts`
Expected: PASS — eleven tests.

- [ ] **Step 6: Mutation-check three things**

1. Change `strict: true` to `strict: false` — the suite should stay green (strict mode guards against future divergence, not today's input). Note the result in your report either way.
2. Remove the ` *` suffix — record whether any test fails. This settles whether chess.js needs it. Report the answer; keep the suffix regardless.
3. Change `Math.floor(i / 2) + 1` to `i + 1` — the move-numbering test must fail.

Restore all three.

- [ ] **Step 7: Verify the import boundary**

Run: `grep -rn "chess.js" src/ --include=*.ts --include=*.tsx`
Expected: exactly one hit, in `replay.ts`.

- [ ] **Step 8: Lint, build, commit**

```bash
yarn test && yarn lint && yarn build
git add package.json yarn.lock src/features/games/replay.ts src/features/games/replay.test.ts
git commit -m "Replay movetext into per-ply positions (#11)"
```

---

### Task 3: `useGame`

**Files:**
- Create: `apps/web/src/features/games/hooks/useGame.ts`
- Test: `apps/web/src/features/games/hooks/useGame.test.ts`

**Interfaces:**
- Consumes: `gamePath`, `fetchGame`, `GameNotFound` (Task 1); `Game` (Task 1); `messageOf` from `src/lib/api.ts`.
- Produces:

```ts
type GameState =
  | { kind: 'loading' }
  | { kind: 'ready'; game: Game }
  | { kind: 'invalid-id' }
  | { kind: 'not-found' }
  | { kind: 'failed'; message: string };

function useGame(id: string | undefined): { state: GameState; retry: () => void }
```

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/hooks/useGame.test.ts`:

```ts
import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useGame } from './useGame';
import type { Game } from '../types/game';

const ID = '11111111-1111-1111-1111-111111111111';

const A_GAME: Game = {
  id: ID,
  white: { playerId: 'w', name: 'Carlsen, M', rating: 2839 },
  black: { playerId: 'b', name: 'Nepomniachtchi, I', rating: 2792 },
  event: 'World Championship',
  site: 'Dubai',
  round: '6',
  playedOn: '2021-12-03',
  result: 'WHITE_WON',
  eco: 'C88',
  source: 'PGN_IMPORT',
  movetext: '1. e4 e5',
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

describe('useGame', () => {
  it('loads, then reports the game', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    const { result } = renderHook(() => useGame(ID));

    expect(result.current.state.kind).toBe('loading');
    await waitFor(() => expect(result.current.state.kind).toBe('ready'));
  });

  it('requests the game by identifier', async () => {
    const fetchStub = vi.fn().mockResolvedValue(jsonResponse(A_GAME));
    vi.stubGlobal('fetch', fetchStub);

    renderHook(() => useGame(ID));

    await waitFor(() => expect(fetchStub).toHaveBeenCalled());
    expect(fetchStub.mock.calls[0][0]).toBe(`/api/games/${ID}`);
  });

  it('refuses a malformed identifier without asking the server', async () => {
    // Retrying a URL that cannot possibly succeed would be misleading.
    const fetchStub = vi.fn();
    vi.stubGlobal('fetch', fetchStub);

    const { result } = renderHook(() => useGame('not-a-uuid'));

    expect(result.current.state.kind).toBe('invalid-id');
    expect(fetchStub).not.toHaveBeenCalled();
  });

  it('refuses a missing identifier the same way', () => {
    const fetchStub = vi.fn();
    vi.stubGlobal('fetch', fetchStub);

    const { result } = renderHook(() => useGame(undefined));

    expect(result.current.state.kind).toBe('invalid-id');
    expect(fetchStub).not.toHaveBeenCalled();
  });

  it('reports a missing game as not-found, not as a failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, 404)));

    const { result } = renderHook(() => useGame(ID));

    await waitFor(() => expect(result.current.state.kind).toBe('not-found'));
  });

  it('reports a transport failure, and retries on demand', async () => {
    const fetchStub = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse(A_GAME));
    vi.stubGlobal('fetch', fetchStub);

    const { result } = renderHook(() => useGame(ID));

    await waitFor(() => expect(result.current.state.kind).toBe('failed'));
    if (result.current.state.kind === 'failed') {
      expect(result.current.state.message).toMatch(/Failed to fetch/);
    }

    result.current.retry();

    await waitFor(() => expect(result.current.state.kind).toBe('ready'));
  });

  it('never lets a superseded response overwrite a newer one', async () => {
    const slow = { promise: null as unknown as Promise<Response>, resolve: (_: Response) => {} };
    slow.promise = new Promise((r) => {
      slow.resolve = r;
    });
    const fast = { promise: null as unknown as Promise<Response>, resolve: (_: Response) => {} };
    fast.promise = new Promise((r) => {
      fast.resolve = r;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise),
    );

    const other = '22222222-2222-2222-2222-222222222222';
    const { result, rerender } = renderHook(({ id }) => useGame(id), {
      initialProps: { id: ID },
    });
    rerender({ id: other });

    fast.resolve(jsonResponse({ ...A_GAME, id: other, event: 'Wijk aan Zee' }));
    await waitFor(() => expect(result.current.state.kind).toBe('ready'));

    slow.resolve(jsonResponse(A_GAME));
    await new Promise((resolve) => setTimeout(resolve, 0));

    if (result.current.state.kind !== 'ready') {
      throw new Error(`expected ready, got ${result.current.state.kind}`);
    }
    expect(result.current.state.game.event).toBe('Wijk aan Zee');
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/hooks/useGame.test.ts`
Expected: FAIL — cannot resolve `./useGame`.

- [ ] **Step 3: Write the hook**

Create `apps/web/src/features/games/hooks/useGame.ts`:

```ts
import { useCallback, useEffect, useState } from 'react';
import { fetchGame, gamePath, GameNotFound } from '../api/games';
import { messageOf } from '../../../lib/api';
import type { Game } from '../types/game';

export type GameState =
  | { kind: 'loading' }
  | { kind: 'ready'; game: Game }
  | { kind: 'invalid-id' }
  | { kind: 'not-found' }
  | { kind: 'failed'; message: string };

export interface UseGame {
  state: GameState;
  retry: () => void;
}

/**
 * The canonical form only. `UUID.fromString` on the backend is lenient — #9
 * records that it widens `1-1-1-1-1` into a valid identifier and answers 404 —
 * so this check is deliberately stricter than the server's. Both answers tell the
 * user the same actionable thing, and this is not the definition of a valid
 * identifier; it only avoids a request that cannot succeed.
 */
const CANONICAL_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * One game's request state.
 *
 * Follows the shape `useGames` established — a discriminated union, an
 * `AbortController` per request, and a `retry` — so both pages behave the same
 * way when the backend is unreachable. It adds two arms the list does not need:
 * an identifier that cannot be valid, and a game that is not there.
 */
export function useGame(id: string | undefined): UseGame {
  const valid = id !== undefined && CANONICAL_UUID.test(id);
  const path = valid ? gamePath(id) : null;
  const [state, setState] = useState<GameState>(valid ? { kind: 'loading' } : { kind: 'invalid-id' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (path === null) {
      setState({ kind: 'invalid-id' });
      return;
    }

    const controller = new AbortController();
    setState((current) => (current.kind === 'loading' ? current : { kind: 'loading' }));

    fetchGame(path, controller.signal)
      .then((game) => {
        if (!controller.signal.aborted) {
          setState({ kind: 'ready', game });
        }
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setState(
          error instanceof GameNotFound
            ? { kind: 'not-found' }
            : { kind: 'failed', message: messageOf(error) },
        );
      });

    return () => controller.abort();
  }, [path, attempt]);

  const retry = useCallback(() => setAttempt((previous) => previous + 1), []);

  return { state, retry };
}
```

If `yarn lint` reports `react(set-state-in-effect)` here, add the same targeted directive `useGames.ts` uses, with a reason — do not restructure the hook:

```ts
// oxlint-disable-next-line react/set-state-in-effect -- the request's outcome is not derivable during render
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/features/games/hooks/useGame.test.ts`
Expected: PASS — seven tests.

- [ ] **Step 5: Mutation-check**

1. Delete the `if (!controller.signal.aborted)` guard on the success path — the superseded-response test must fail.
2. Replace `error instanceof GameNotFound ? { kind: 'not-found' } : …` with the failure arm unconditionally — the not-found test must fail.
3. Remove the `CANONICAL_UUID.test(id)` guard — the malformed-identifier test must fail.

Restore all three.

- [ ] **Step 6: Lint, build, commit**

```bash
yarn test && yarn lint && yarn build
git add src/features/games/hooks/useGame.ts src/features/games/hooks/useGame.test.ts
git commit -m "Game detail request state (#11)"
```

---

### Task 4: `useReplay`

**Files:**
- Create: `apps/web/src/features/games/hooks/useReplay.ts`
- Test: `apps/web/src/features/games/hooks/useReplay.test.ts`

**Interfaces:**
- Consumes: `Ply` from `../types/game` (Task 1).
- Produces: `useReplay(plies: Ply[]): { current: number; select: (index: number) => void }`

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/hooks/useReplay.test.ts`:

```ts
import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useReplay } from './useReplay';
import type { Ply } from '../types/game';

function plies(count: number): Ply[] {
  return Array.from({ length: count }, (_, index) => ({
    index,
    moveNumber: Math.ceil(index / 2),
    colour: index === 0 ? null : index % 2 === 1 ? ('white' as const) : ('black' as const),
    san: index === 0 ? null : `move${index}`,
    fen: `fen${index}`,
  }));
}

describe('useReplay', () => {
  it('starts at the initial position', () => {
    const { result } = renderHook(() => useReplay(plies(5)));

    expect(result.current.current).toBe(0);
  });

  it('selects a ply', () => {
    const { result } = renderHook(() => useReplay(plies(5)));

    act(() => result.current.select(3));

    expect(result.current.current).toBe(3);
  });

  it('refuses an index past the end rather than producing an undefined position', () => {
    const { result } = renderHook(() => useReplay(plies(5)));

    act(() => result.current.select(3));
    act(() => result.current.select(99));

    expect(result.current.current).toBe(3);
  });

  it('refuses a negative index', () => {
    const { result } = renderHook(() => useReplay(plies(5)));

    act(() => result.current.select(2));
    act(() => result.current.select(-1));

    expect(result.current.current).toBe(2);
  });

  it('never reports an index past a shorter set of plies', () => {
    // Navigating from a long game to a short one must not index past the end.
    const { result, rerender } = renderHook(({ p }) => useReplay(p), {
      initialProps: { p: plies(40) },
    });

    act(() => result.current.select(39));
    rerender({ p: plies(5) });

    expect(result.current.current).toBeLessThan(5);
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/hooks/useReplay.test.ts`
Expected: FAIL — cannot resolve `./useReplay`.

- [ ] **Step 3: Write the hook**

Create `apps/web/src/features/games/hooks/useReplay.ts`:

```ts
import { useCallback, useState } from 'react';
import type { Ply } from '../types/game';

export interface UseReplay {
  current: number;
  select: (index: number) => void;
}

/**
 * Which ply is being shown.
 *
 * Takes plies rather than movetext, so it never touches chess.js: replaying is
 * `replay`'s job, and this only holds a cursor into the result.
 *
 * Out-of-range selections are refused rather than clamped silently at the call
 * site, and the reported index is bounded by the current plies. The page also
 * keys the viewer by game id, so this is the second line of defence against a
 * stale index surviving a move to a shorter game — not the first.
 */
export function useReplay(plies: Ply[]): UseReplay {
  const [current, setCurrent] = useState(0);

  const select = useCallback(
    (index: number) => {
      setCurrent((previous) => (index >= 0 && index < plies.length ? index : previous));
    },
    [plies.length],
  );

  return { current: Math.min(current, Math.max(plies.length - 1, 0)), select };
}
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/features/games/hooks/useReplay.test.ts`
Expected: PASS — five tests.

- [ ] **Step 5: Mutation-check**

Remove the `index < plies.length` condition — the "refuses an index past the end" test must fail. Then remove the `Math.min(...)` bound — the "shorter set of plies" test must fail. Restore both.

- [ ] **Step 6: Lint, build, commit**

```bash
yarn test && yarn lint && yarn build
git add src/features/games/hooks/useReplay.ts src/features/games/hooks/useReplay.test.ts
git commit -m "Ply selection for the game viewer (#11)"
```

---

### Task 5: `Chessboard` and the piece assets

**Files:**
- Create: `apps/web/src/features/games/components/Chessboard.tsx`
- Create: `apps/web/src/features/games/components/Chessboard.module.css`
- Create: `apps/web/public/pieces/{w,b}{p,n,b,r,q,k}.svg` (12 files)
- Create: `apps/web/public/pieces/LICENCE`
- Modify: `apps/web/src/index.css` (two new tokens)
- Test: `apps/web/src/features/games/components/Chessboard.test.tsx`

**Interfaces:**
- Consumes: nothing but React.
- Produces: `Chessboard({ fen }: { fen: string })`, and `squaresOf(placement: string): Square[] | null` exported for its own test.

- [ ] **Step 1: Vendor the piece assets**

Download the twelve Cburnett SVGs from Wikimedia Commons into `apps/web/public/pieces/`, named `wp.svg wn.svg wb.svg wr.svg wq.svg wk.svg bp.svg bn.svg bb.svg br.svg bq.svg bk.svg`. The Commons files are named `Chess_plt45.svg` (white pawn), `Chess_nlt45.svg`, `Chess_blt45.svg`, `Chess_rlt45.svg`, `Chess_qlt45.svg`, `Chess_klt45.svg`, and the same with `d` for dark: `Chess_pdt45.svg` and so on.

Create `apps/web/public/pieces/LICENCE`:

```
Chess piece images
------------------
Author:  Cburnett (https://commons.wikimedia.org/wiki/User:Cburnett)
Source:  https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces
Licence: BSD 3-Clause

These files are multi-licensed on Wikimedia Commons under GFDL 1.2+,
CC-BY-SA 3.0, the 3-clause BSD Licence, and GPL v2+, at the recipient's
option. This project takes them under the 3-clause BSD Licence, whose
only obligation is to retain the copyright notice — reproduced here so
that redistributing this repository satisfies it.

Copyright (c) Cburnett

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.
3. Neither the name of the author nor the names of contributors may be
   used to endorse or promote products derived from this software
   without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

If any file's Commons page does **not** offer the BSD option, stop and report it rather than substituting a differently licensed set.

- [ ] **Step 2: Write the failing test**

Create `apps/web/src/features/games/components/Chessboard.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Chessboard, squaresOf } from './Chessboard';

const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
const EMPTY = '8/8/8/8/8/8/8/8 w - - 0 1';

describe('squaresOf', () => {
  it('expands a placement into 64 squares, a8 first', () => {
    const squares = squaresOf(START.split(' ')[0])!;

    expect(squares).toHaveLength(64);
    expect(squares[0]).toMatchObject({ name: 'a8', piece: 'r' });
    expect(squares[63]).toMatchObject({ name: 'h1', piece: 'R' });
  });

  it('expands digit runs into empty squares', () => {
    const squares = squaresOf(EMPTY.split(' ')[0])!;

    expect(squares).toHaveLength(64);
    expect(squares.every((square) => square.piece === null)).toBe(true);
  });

  it('alternates square colour, with a1 dark', () => {
    const squares = squaresOf(START.split(' ')[0])!;
    const a1 = squares.find((square) => square.name === 'a1')!;
    const h1 = squares.find((square) => square.name === 'h1')!;

    expect(a1.light).toBe(false);
    expect(h1.light).toBe(true);
  });

  it('refuses a placement that does not describe a board', () => {
    // A shifted or incomplete board must fail, not render quietly wrong.
    expect(squaresOf('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP')).toBeNull();
    expect(squaresOf('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNRR')).toBeNull();
    expect(squaresOf('')).toBeNull();
  });
});

describe('Chessboard', () => {
  it('places the pieces the FEN describes', () => {
    render(<Chessboard fen={START} />);

    expect(screen.getByLabelText('a1, white rook')).toBeInTheDocument();
    expect(screen.getByLabelText('e8, black king')).toBeInTheDocument();
  });

  it('names each occupied square by coordinate and piece', () => {
    // A screen reader hearing "white pawn" 8 times learns nothing about where.
    render(<Chessboard fen={START} />);

    expect(screen.getByLabelText('e2, white pawn')).toBeInTheDocument();
    expect(screen.getByLabelText('d7, black pawn')).toBeInTheDocument();
  });

  it('announces nothing for empty squares', () => {
    render(<Chessboard fen={EMPTY} />);

    expect(screen.queryAllByRole('img')).toHaveLength(0);
  });

  it('has an accessible name identifying it as the position', () => {
    render(<Chessboard fen={START} />);

    expect(screen.getByRole('group', { name: /position/i })).toBeInTheDocument();
  });

  it('says so when the position cannot be read', () => {
    render(<Chessboard fen="not-a-fen" />);

    expect(screen.getByText(/could not be read/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 3: Run the test and watch it fail**

Run: `yarn test src/features/games/components/Chessboard.test.tsx`
Expected: FAIL — cannot resolve `./Chessboard`.

- [ ] **Step 4: Write the component**

Create `apps/web/src/features/games/components/Chessboard.tsx`:

```tsx
import styles from './Chessboard.module.css';

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];

const PIECE_NAMES: Record<string, string> = {
  p: 'pawn',
  n: 'knight',
  b: 'bishop',
  r: 'rook',
  q: 'queen',
  k: 'king',
};

export interface Square {
  name: string;
  /** The FEN letter, case carrying colour, or null for an empty square. */
  piece: string | null;
  light: boolean;
}

/**
 * A FEN's placement field as 64 squares, a8 first and h1 last — reading order.
 *
 * Returns null rather than a partial board when the placement does not describe
 * eight ranks of eight squares. A board drawn from a bad placement is shifted
 * rather than obviously broken, which is the worst of both outcomes.
 */
export function squaresOf(placement: string): Square[] | null {
  const ranks = placement.split('/');
  if (ranks.length !== 8) {
    return null;
  }

  const squares: Square[] = [];
  for (let rankIndex = 0; rankIndex < 8; rankIndex++) {
    const rankNumber = 8 - rankIndex;
    let file = 0;

    for (const character of ranks[rankIndex]) {
      if (character >= '1' && character <= '8') {
        const run = Number(character);
        for (let i = 0; i < run; i++) {
          squares.push(square(file + i, rankNumber, null));
        }
        file += run;
      } else if (PIECE_NAMES[character.toLowerCase()] !== undefined) {
        squares.push(square(file, rankNumber, character));
        file += 1;
      } else {
        return null;
      }
    }

    if (file !== 8) {
      return null;
    }
  }

  return squares;
}

function square(file: number, rankNumber: number, piece: string | null): Square {
  return {
    name: `${FILES[file]}${rankNumber}`,
    piece,
    // a1 (file 0, rank 1) sums to 1 and is dark; h1 sums to 8 and is light.
    light: (file + rankNumber) % 2 === 0,
  };
}

function describe(piece: string): string {
  const colour = piece === piece.toUpperCase() ? 'white' : 'black';
  return `${colour} ${PIECE_NAMES[piece.toLowerCase()]}`;
}

function source(piece: string): string {
  const colour = piece === piece.toUpperCase() ? 'w' : 'b';
  return `/pieces/${colour}${piece.toLowerCase()}.svg`;
}

/**
 * A position, drawn.
 *
 * Takes a FEN string and nothing else — no game, no chess.js, no fetching. That
 * is what lets #17 hand it a position from a half-recognised scoresheet that is
 * not yet a legal game.
 *
 * Only the placement field is read, which is why this component needs no chess
 * library: expanding it is a string operation.
 */
export function Chessboard({ fen }: { fen: string }) {
  const squares = squaresOf(fen.split(' ')[0]);

  if (squares === null) {
    return <p className={styles.unreadable}>This position could not be read.</p>;
  }

  return (
    <div className={styles.board} role="group" aria-label="Chess position">
      {squares.map((square) => (
        <div
          key={square.name}
          className={square.light ? styles.light : styles.dark}
          {...(square.piece !== null
            ? { role: 'img', 'aria-label': `${square.name}, ${describe(square.piece)}` }
            : {})}
        >
          {square.piece !== null && <img src={source(square.piece)} alt="" />}
          {square.name[1] === '1' && <span className={styles.file}>{square.name[0]}</span>}
          {square.name[0] === 'a' && <span className={styles.rank}>{square.name[1]}</span>}
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 5: Add the board colours and the stylesheet**

In `apps/web/src/index.css`, add to the `:root` block, beside the existing tokens:

```css
  --board-light: #ebecd0;
  --board-dark: #779556;
```

And inside the `@media (prefers-color-scheme: dark)` `:root` block:

```css
    --board-light: #b6bda0;
    --board-dark: #55693e;
```

Create `apps/web/src/features/games/components/Chessboard.module.css`:

```css
.board {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  width: min(480px, 100%);
  aspect-ratio: 1;
  border: 1px solid var(--border);
}

.light,
.dark {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.light {
  background: var(--board-light);
}

.dark {
  background: var(--board-dark);
}

.light img,
.dark img {
  width: 100%;
  height: 100%;
}

.file,
.rank {
  position: absolute;
  font-size: 10px;
  color: var(--text-h);
  opacity: 0.7;
}

.file {
  right: 2px;
  bottom: 0;
}

.rank {
  left: 2px;
  top: 0;
}

.unreadable {
  color: var(--text);
}
```

- [ ] **Step 6: Run the test**

Run: `yarn test src/features/games/components/Chessboard.test.tsx`
Expected: PASS — nine tests.

- [ ] **Step 7: Mutation-check**

1. Change `light: (file + rankNumber) % 2 === 0` to `=== 1` — the square-colour test must fail.
2. Remove the `if (file !== 8) return null;` guard — the "refuses a placement" test must fail.
3. Drop the coordinate from the aria-label, leaving only the piece — the "names each occupied square" test must fail.

Restore all three.

- [ ] **Step 8: Lint, build, commit**

```bash
yarn test && yarn lint && yarn build
git add src/features/games/components/Chessboard.tsx src/features/games/components/Chessboard.module.css src/features/games/components/Chessboard.test.tsx src/index.css public/pieces
git commit -m "Chessboard component and vendored piece set (#11)"
```

---

### Task 6: `MoveList`

**Files:**
- Create: `apps/web/src/features/games/components/MoveList.tsx`
- Create: `apps/web/src/features/games/components/MoveList.module.css`
- Test: `apps/web/src/features/games/components/MoveList.test.tsx`

**Interfaces:**
- Consumes: `Ply` from `../types/game` (Task 1).
- Produces: `MoveList({ plies, current, onSelect }: { plies: Ply[]; current: number; onSelect: (index: number) => void })`

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/components/MoveList.test.tsx`:

```tsx
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MoveList } from './MoveList';
import type { Ply } from '../types/game';

const PLIES: Ply[] = [
  { index: 0, moveNumber: 0, colour: null, san: null, fen: 'initial' },
  { index: 1, moveNumber: 1, colour: 'white', san: 'e4', fen: 'f1' },
  { index: 2, moveNumber: 1, colour: 'black', san: 'e5', fen: 'f2' },
  { index: 3, moveNumber: 2, colour: 'white', san: 'Nf3', fen: 'f3' },
  { index: 4, moveNumber: 2, colour: 'black', san: 'Nc6', fen: 'f4' },
  { index: 5, moveNumber: 3, colour: 'white', san: 'Bb5', fen: 'f5' },
];

describe('MoveList', () => {
  it('pairs the moves by move number', () => {
    render(<MoveList plies={PLIES} current={0} onSelect={vi.fn()} />);

    const rows = screen.getAllByRole('row');
    expect(within(rows[0]).getByText('1')).toBeInTheDocument();
    expect(within(rows[0]).getByRole('button', { name: 'e4' })).toBeInTheDocument();
    expect(within(rows[0]).getByRole('button', { name: 'e5' })).toBeInTheDocument();
  });

  it('leaves the black cell empty when the game ends on a white move', () => {
    render(<MoveList plies={PLIES} current={0} onSelect={vi.fn()} />);

    const rows = screen.getAllByRole('row');
    expect(within(rows[2]).getByRole('button', { name: 'Bb5' })).toBeInTheDocument();
    expect(within(rows[2]).getAllByRole('button')).toHaveLength(1);
  });

  it('offers the initial position, so the start is reachable', () => {
    render(<MoveList plies={PLIES} current={3} onSelect={vi.fn()} />);

    expect(screen.getByRole('button', { name: /start/i })).toBeInTheDocument();
  });

  it('marks the current ply for a screen reader, not by colour alone', () => {
    render(<MoveList plies={PLIES} current={3} onSelect={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Nf3' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('button', { name: 'e4' })).not.toHaveAttribute('aria-current');
  });

  it('raises the index of the clicked ply', async () => {
    const onSelect = vi.fn();
    render(<MoveList plies={PLIES} current={0} onSelect={onSelect} />);

    await userEvent.click(screen.getByRole('button', { name: 'Nc6' }));

    expect(onSelect).toHaveBeenCalledExactlyOnceWith(4);
  });

  it('raises 0 for the initial position', async () => {
    const onSelect = vi.fn();
    render(<MoveList plies={PLIES} current={3} onSelect={onSelect} />);

    await userEvent.click(screen.getByRole('button', { name: /start/i }));

    expect(onSelect).toHaveBeenCalledExactlyOnceWith(0);
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/components/MoveList.test.tsx`
Expected: FAIL — cannot resolve `./MoveList`.

- [ ] **Step 3: Write the component**

Create `apps/web/src/features/games/components/MoveList.tsx`:

```tsx
import styles from './MoveList.module.css';
import type { Ply } from '../types/game';

interface MoveRow {
  moveNumber: number;
  white: Ply | null;
  black: Ply | null;
}

/**
 * Plies grouped into scoresheet rows. Index 0 is the initial position and is not
 * a move, so it never appears in a row.
 */
function rowsOf(plies: Ply[]): MoveRow[] {
  const rows = new Map<number, MoveRow>();

  for (const ply of plies) {
    if (ply.colour === null) {
      continue;
    }
    const row = rows.get(ply.moveNumber) ?? {
      moveNumber: ply.moveNumber,
      white: null,
      black: null,
    };
    if (ply.colour === 'white') {
      row.white = ply;
    } else {
      row.black = ply;
    }
    rows.set(ply.moveNumber, row);
  }

  return [...rows.values()];
}

export interface MoveListProps {
  plies: Ply[];
  current: number;
  onSelect: (index: number) => void;
}

/**
 * The moves, laid out the way a scoresheet is: move number, White, Black. That
 * shape is not incidental — #17 puts a scoresheet image beside this component.
 *
 * Takes plies and an index, so it is decoupled from how the game was loaded.
 */
export function MoveList({ plies, current, onSelect }: MoveListProps) {
  return (
    <div className={styles.moves}>
      <button
        type="button"
        className={styles.start}
        onClick={() => onSelect(0)}
        {...(current === 0 ? { 'aria-current': 'true' as const } : {})}
      >
        Start
      </button>
      <table className={styles.table}>
        <caption className={styles.caption}>Moves</caption>
        <tbody>
          {rowsOf(plies).map((row) => (
            <tr key={row.moveNumber}>
              <th scope="row" className={styles.number}>
                {row.moveNumber}
              </th>
              <td>{row.white !== null && moveButton(row.white, current, onSelect)}</td>
              <td>{row.black !== null && moveButton(row.black, current, onSelect)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function moveButton(ply: Ply, current: number, onSelect: (index: number) => void) {
  return (
    <button
      type="button"
      className={styles.move}
      onClick={() => onSelect(ply.index)}
      {...(ply.index === current ? { 'aria-current': 'true' as const } : {})}
    >
      {ply.san}
    </button>
  );
}
```

Create `apps/web/src/features/games/components/MoveList.module.css`:

```css
.moves {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.table {
  border-collapse: collapse;
  font-size: 15px;
  text-align: left;
}

.caption {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  margin: -1px;
}

.number {
  padding: 2px 8px 2px 0;
  color: var(--text);
  font-weight: 400;
  text-align: right;
}

.move,
.start {
  font: inherit;
  font-size: 15px;
  color: var(--text-h);
  background: none;
  border: 1px solid transparent;
  border-radius: 4px;
  padding: 2px 8px;
  cursor: pointer;
}

.move:hover,
.start:hover {
  border-color: var(--border);
}

.move:focus-visible,
.start:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.move[aria-current='true'],
.start[aria-current='true'] {
  background: var(--accent-bg);
  border-color: var(--accent-border);
}
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/features/games/components/MoveList.test.tsx`
Expected: PASS — six tests.

- [ ] **Step 5: Mutation-check**

1. Drop the `aria-current` attribute from `moveButton` — the "marks the current ply" test must fail.
2. Change `onSelect(ply.index)` to `onSelect(ply.moveNumber)` — the "raises the index" test must fail.

Restore both.

- [ ] **Step 6: Lint, build, commit**

```bash
yarn test && yarn lint && yarn build
git add src/features/games/components/MoveList.tsx src/features/games/components/MoveList.module.css src/features/games/components/MoveList.test.tsx
git commit -m "Move list for the game viewer (#11)"
```

---

### Task 7: `GameHeader`

**Files:**
- Create: `apps/web/src/features/games/components/GameHeader.tsx`
- Create: `apps/web/src/features/games/components/GameHeader.module.css`
- Test: `apps/web/src/features/games/components/GameHeader.test.tsx`

**Interfaces:**
- Consumes: `Game` (Task 1); `sideLabel`, `resultLabel`, `sourceLabel`, `orDash` from `../format`.
- Produces: `GameHeader({ game }: { game: Game })`

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/components/GameHeader.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GameHeader } from './GameHeader';
import type { Game } from '../types/game';

const COMPLETE: Game = {
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
  movetext: '1. e4 e5',
};

const SPARSE: Game = {
  ...COMPLETE,
  white: { playerId: 'w', name: 'Green, G', rating: null },
  black: { playerId: 'b', name: 'Opponent, O', rating: null },
  event: null,
  site: null,
  round: null,
  playedOn: null,
  eco: null,
  result: 'UNFINISHED',
};

describe('GameHeader', () => {
  it('names both players with their game-time ratings', () => {
    render(<GameHeader game={COMPLETE} />);

    expect(screen.getByText('Carlsen, M (2839)')).toBeInTheDocument();
    expect(screen.getByText('Nepomniachtchi, I (2792)')).toBeInTheDocument();
  });

  it('shows the result as its display token', () => {
    render(<GameHeader game={COMPLETE} />);

    expect(screen.getByText('1-0')).toBeInTheDocument();
  });

  it('shows the metadata that was recorded', () => {
    render(<GameHeader game={COMPLETE} />);

    expect(screen.getByText(/World Championship/)).toBeInTheDocument();
    expect(screen.getByText(/Dubai/)).toBeInTheDocument();
    expect(screen.getByText('2021-12-03')).toBeInTheDocument();
    expect(screen.getByText('C88')).toBeInTheDocument();
  });

  it('renders absent metadata as an em dash rather than an empty gap', () => {
    render(<GameHeader game={SPARSE} />);

    // event, site, round, date and ECO were all absent.
    expect(screen.getAllByText('—')).toHaveLength(5);
  });

  it('omits the parentheses for a player with no recorded rating', () => {
    render(<GameHeader game={SPARSE} />);

    expect(screen.getByText('Green, G')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/components/GameHeader.test.tsx`
Expected: FAIL — cannot resolve `./GameHeader`.

- [ ] **Step 3: Write the component**

Create `apps/web/src/features/games/components/GameHeader.tsx`:

```tsx
import styles from './GameHeader.module.css';
import { orDash, resultLabel, sideLabel, sourceLabel } from '../format';
import type { Game } from '../types/game';

/**
 * Who played, where, and how it finished.
 *
 * Uses the same formatting helpers as the list, so a game reads identically in
 * both places.
 */
export function GameHeader({ game }: { game: Game }) {
  return (
    <header className={styles.header}>
      <h2 className={styles.players}>
        <span>{sideLabel(game.white)}</span>
        <span className={styles.result}>{resultLabel(game.result)}</span>
        <span>{sideLabel(game.black)}</span>
      </h2>
      <dl className={styles.meta}>
        <div>
          <dt>Event</dt>
          <dd>{orDash(game.event)}</dd>
        </div>
        <div>
          <dt>Site</dt>
          <dd>{orDash(game.site)}</dd>
        </div>
        <div>
          <dt>Round</dt>
          <dd>{orDash(game.round)}</dd>
        </div>
        <div>
          <dt>Date</dt>
          <dd>{orDash(game.playedOn)}</dd>
        </div>
        <div>
          <dt>ECO</dt>
          <dd>{orDash(game.eco)}</dd>
        </div>
        <div>
          <dt>Source</dt>
          <dd>{sourceLabel(game.source)}</dd>
        </div>
      </dl>
    </header>
  );
}
```

Create `apps/web/src/features/games/components/GameHeader.module.css`:

```css
.header {
  margin-bottom: 16px;
}

.players {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 12px;
  margin: 0 0 8px;
}

.result {
  color: var(--text);
  font-family: var(--mono);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 0;
  font-size: 14px;
}

.meta dt {
  color: var(--text);
}

.meta dd {
  margin: 0;
  color: var(--text-h);
}
```

- [ ] **Step 4: Run the test**

Run: `yarn test src/features/games/components/GameHeader.test.tsx`
Expected: PASS — five tests.

- [ ] **Step 5: Mutation-check**

Replace `orDash(game.event)` with `game.event` — the em-dash test must fail. Restore it.

- [ ] **Step 6: Lint, build, commit**

```bash
yarn test && yarn lint && yarn build
git add src/features/games/components/GameHeader.tsx src/features/games/components/GameHeader.module.css src/features/games/components/GameHeader.test.tsx
git commit -m "Game header for the viewer (#11)"
```

---

### Task 8: Routing, and the row's way in

The only task touching files outside the games feature, and the one that changes an existing test.

**Files:**
- Modify: `apps/web/package.json` (add `react-router`)
- Create: `apps/web/src/app/HomePage.tsx`
- Modify: `apps/web/src/app/App.tsx` (replace)
- Modify: `apps/web/src/app/App.test.tsx` (replace)
- Modify: `apps/web/src/features/games/components/GameRow.tsx`
- Modify: `apps/web/src/features/games/components/GameTable.tsx`
- Modify: `apps/web/src/features/games/components/GameTable.test.tsx`

**Interfaces:**
- Consumes: `GamesPage`, `BackendHealthCard`, `useBackendHealth`, `GameSummary`.
- Produces: routes `/` and `/games/:id`; `HomePage()`.

**Why the row is not itself a link.** An `<a>` cannot validly wrap or replace a `<tr>`. #10's spec promised "`GameRow` becomes a `<Link>`", which is impossible HTML. The row keeps its semantics and gains a final cell containing the link.

- [ ] **Step 1: Add the dependency**

Run: `yarn add react-router@^8`
Confirm `git diff package.json` shows one added line.

- [ ] **Step 2: Write the failing tests**

Replace `apps/web/src/app/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';

function jsonResponse(body: unknown): Response {
  return { ok: true, status: 200, json: async () => body } as unknown as Response;
}

const EMPTY_PAGE = { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 };

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.pushState({}, '', '/');
});

describe('App', () => {
  it('renders the application name', () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    render(<App />);

    expect(screen.getByRole('heading', { name: /chess prep/i })).toBeInTheDocument();
  });

  it('shows the games list at the root', () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_PAGE)));

    render(<App />);

    expect(screen.getByRole('heading', { name: /games/i })).toBeInTheDocument();
  });

  it('shows the viewer at /games/:id', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    window.history.pushState({}, '', '/games/11111111-1111-1111-1111-111111111111');

    render(<App />);

    // The viewer is what fetches a single game; the list never does.
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /^games$/i })).not.toBeInTheDocument();
  });
});
```

Add to `apps/web/src/features/games/components/GameTable.test.tsx` — and **update the existing header assertion from nine columns to ten**, keeping its order check and its `scope="col"` loop intact:

```tsx
  it('gives every row a way into the viewer', () => {
    render(
      <MemoryRouter>
        <GameTable games={[COMPLETE]} />
      </MemoryRouter>,
    );

    const link = screen.getByRole('link', { name: /Carlsen, M/ });
    expect(link).toHaveAttribute('href', '/games/1');
  });
```

`GameTable.test.tsx` and any other test rendering `GameRow` now need a router context: import `MemoryRouter` from `react-router` and wrap the render.

- [ ] **Step 3: Run the tests and watch them fail**

Run: `yarn test src/app/App.test.tsx src/features/games/components/GameTable.test.tsx`
Expected: FAIL — no routes, no link, and the header assertion still expects nine columns.

- [ ] **Step 4: Add the link cell**

In `apps/web/src/features/games/components/GameRow.tsx`, import `Link` from `react-router`, add the cell, and replace the now-wrong doc comment:

```tsx
import { Link } from 'react-router';
import { orDash, resultLabel, sideLabel, sourceLabel } from '../format';
import type { GameSummary } from '../types/game';

/**
 * One game as a table row.
 *
 * The link lives in its own cell rather than wrapping the row: an anchor cannot
 * validly wrap or replace a `<tr>`. One explicit target per row also gives a
 * keyboard user a single stop rather than a link in every cell.
 *
 * The accessible name names the game, because a screen reader listing links
 * would otherwise read "View" once per row.
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
      <td>
        <Link
          to={`/games/${game.id}`}
          aria-label={`View ${game.white.name} versus ${game.black.name}`}
        >
          View
        </Link>
      </td>
    </tr>
  );
}
```

In `apps/web/src/features/games/components/GameTable.tsx`, add the tenth header after `Source`:

```tsx
          <th scope="col">View</th>
```

- [ ] **Step 5: Add the routes**

Create `apps/web/src/app/HomePage.tsx`:

```tsx
import { BackendHealthCard } from '../features/system-health/components/BackendHealthCard';
import { useBackendHealth } from '../features/system-health/hooks/useBackendHealth';
import { GamesPage } from '../features/games/pages/GamesPage';

/**
 * The list, with the backend's health beneath it.
 *
 * Health lives here rather than in `App` so that opening a game does not also
 * poll Actuator.
 */
export function HomePage() {
  const health = useBackendHealth();

  return (
    <>
      <GamesPage />
      <BackendHealthCard state={health} />
    </>
  );
}
```

Replace `apps/web/src/app/App.tsx`:

```tsx
import { BrowserRouter, Route, Routes } from 'react-router';
import { HomePage } from './HomePage';
import { GameViewerPage } from '../features/games/pages/GameViewerPage';

/**
 * The shell and the routes.
 *
 * Browser history, not hash routing — which is a deployment requirement as much
 * as a code one: any static host serving this must rewrite unknown application
 * paths such as `/games/{id}` to `index.html` while leaving `/api/*` to the
 * backend. Vite's dev server already does. Without that rule, in-app navigation
 * works but refreshing or sharing a viewer URL returns the host's 404.
 */
export default function App() {
  return (
    <BrowserRouter>
      <main>
        <h1>Chess Prep</h1>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/games/:id" element={<GameViewerPage />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}
```

**`GameViewerPage` does not exist until Task 9.** To keep this task's build green, create a minimal placeholder now and let Task 9 replace it wholesale:

```tsx
// apps/web/src/features/games/pages/GameViewerPage.tsx — replaced in full by Task 9
import { useParams } from 'react-router';
import { useGame } from '../hooks/useGame';

export function GameViewerPage() {
  const { id } = useParams();
  const { state } = useGame(id);
  return <div role={state.kind === 'failed' ? 'alert' : undefined}>{state.kind}</div>;
}
```

- [ ] **Step 6: Run the whole suite**

Run: `yarn test`
Expected: PASS. If any test rendering `GameRow`, `GameTable` or `GamesPage` now fails with a missing router context, wrap its render in `<MemoryRouter>` — do not remove the link.

- [ ] **Step 7: Mutation-check**

1. Remove the `<Link>` cell from `GameRow` — the "way into the viewer" test must fail.
2. Change the `/games/:id` route path to `/game/:id` — the viewer route test must fail.
3. Delete one `<th scope="col">` — the header test must fail, as it did before this task.

Restore all three.

- [ ] **Step 8: Lint, build, commit**

```bash
yarn test && yarn lint && yarn build
git add package.json yarn.lock src/app src/features/games/components src/features/games/pages/GameViewerPage.tsx
git commit -m "Routing, and a way from a row into the viewer (#11)"
```

---

### Task 9: `GameViewerPage`

The composition, and the first test that drives the real hooks end to end.

**Files:**
- Modify: `apps/web/src/features/games/pages/GameViewerPage.tsx` (replace the Task 8 placeholder)
- Create: `apps/web/src/features/games/pages/GameViewerPage.module.css`
- Test: `apps/web/src/features/games/pages/GameViewerPage.test.tsx`

**Interfaces:**
- Consumes: `useGame` (Task 3), `useReplay` (Task 4), `replay` (Task 2), `Chessboard` (Task 5), `MoveList` (Task 6), `GameHeader` (Task 7), `useParams` from `react-router`.
- Produces: `GameViewerPage()` — no props.

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/features/games/pages/GameViewerPage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GameViewerPage } from './GameViewerPage';
import type { Game } from '../types/game';

const ID = '11111111-1111-1111-1111-111111111111';

const A_GAME: Game = {
  id: ID,
  white: { playerId: 'w', name: 'Carlsen, M', rating: 2839 },
  black: { playerId: 'b', name: 'Nepomniachtchi, I', rating: 2792 },
  event: 'World Championship',
  site: 'Dubai',
  round: '6',
  playedOn: '2021-12-03',
  result: 'WHITE_WON',
  eco: 'C88',
  source: 'PGN_IMPORT',
  movetext: '1. e4 e5 2. Nf3 Nc6 3. Bb5',
};

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

function renderAt(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/games/${id}`]}>
      <Routes>
        <Route path="/games/:id" element={<GameViewerPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('GameViewerPage', () => {
  it('shows the game, its board and its moves', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);

    expect(await screen.findByText('Carlsen, M (2839)')).toBeInTheDocument();
    expect(screen.getByRole('group', { name: /position/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Nf3' })).toBeInTheDocument();
  });

  it('starts at the initial position', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);

    await screen.findByRole('group', { name: /position/i });
    // Every piece is still on its starting square.
    expect(screen.getByLabelText('e2, white pawn')).toBeInTheDocument();
  });

  it('shows the position of the move that was clicked', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);

    await userEvent.click(await screen.findByRole('button', { name: 'e4' }));

    expect(screen.getByLabelText('e4, white pawn')).toBeInTheDocument();
    expect(screen.queryByLabelText('e2, white pawn')).not.toBeInTheDocument();
  });

  it('says the game is not here, and offers no retry for it', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, 404)));

    renderAt(ID);

    expect(await screen.findByText(/no game with that identifier/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /games/i })).toBeInTheDocument();
  });

  it('refuses a malformed identifier without asking the server', async () => {
    const fetchStub = vi.fn();
    vi.stubGlobal('fetch', fetchStub);

    renderAt('not-a-uuid');

    expect(await screen.findByText(/identifier is invalid/i)).toBeInTheDocument();
    expect(fetchStub).not.toHaveBeenCalled();
  });

  it('offers a retry when the request failed, and recovers', async () => {
    const fetchStub = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse(A_GAME));
    vi.stubGlobal('fetch', fetchStub);

    renderAt(ID);

    expect(await screen.findByRole('alert')).toHaveTextContent(/failed to fetch/i);

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByText('Carlsen, M (2839)')).toBeInTheDocument();
  });

  it('shows what it can when the moves cannot be replayed', async () => {
    const unreplayable = { ...A_GAME, movetext: '1. e4 e5 2. Qxf7' };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(unreplayable)));

    renderAt(ID);

    // The header still renders, the stored movetext is visible, and the failure
    // is explained rather than the page going blank.
    expect(await screen.findByText('Carlsen, M (2839)')).toBeInTheDocument();
    expect(screen.getByText(/1\. e4 e5 2\. Qxf7/)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `yarn test src/features/games/pages/GameViewerPage.test.tsx`
Expected: FAIL — the Task 8 placeholder renders a state name, not a viewer.

- [ ] **Step 3: Write the page**

Replace `apps/web/src/features/games/pages/GameViewerPage.tsx`:

```tsx
import { useMemo } from 'react';
import { Link, useParams } from 'react-router';
import styles from './GameViewerPage.module.css';
import { Chessboard } from '../components/Chessboard';
import { GameHeader } from '../components/GameHeader';
import { MoveList } from '../components/MoveList';
import { useGame } from '../hooks/useGame';
import { useReplay } from '../hooks/useReplay';
import { replay } from '../replay';
import type { Game } from '../types/game';

/**
 * One game, replayed.
 *
 * The page owns every state that is about the request rather than about a board:
 * an identifier that cannot be valid, a game that is not here, a request that
 * failed, and a game whose moves would not replay.
 */
export function GameViewerPage() {
  const { id } = useParams();
  const { state, retry } = useGame(id);

  if (state.kind === 'loading') {
    return <p role="status">Loading game…</p>;
  }

  if (state.kind === 'invalid-id') {
    return (
      <section className={styles.problem}>
        <p>That game identifier is invalid.</p>
        <Link to="/">Back to games</Link>
      </section>
    );
  }

  if (state.kind === 'not-found') {
    return (
      <section className={styles.problem}>
        <p>No game with that identifier.</p>
        <Link to="/">Back to games</Link>
      </section>
    );
  }

  if (state.kind === 'failed') {
    return (
      <section className={styles.problem} role="alert">
        <p>{state.message}</p>
        <button type="button" onClick={retry}>
          Retry
        </button>
      </section>
    );
  }

  // Keyed by game id so a stale ply index cannot survive a move to another game.
  return <GameViewer key={state.game.id} game={state.game} />;
}

/**
 * A loaded game. Separate so that the key above resets its selection, and so
 * that the replay runs once per game rather than once per render.
 */
function GameViewer({ game }: { game: Game }) {
  const replayed = useMemo(() => replay(game.movetext), [game.movetext]);
  const { current, select } = useReplay(replayed.plies);

  return (
    <article className={styles.viewer}>
      <GameHeader game={game} />

      {replayed.error !== null ? (
        <section className={styles.unreplayable}>
          <div role="alert">
            <p>These moves could not be replayed: {replayed.error}</p>
          </div>
          <pre className={styles.movetext}>{game.movetext}</pre>
          <Chessboard fen={replayed.plies[0].fen} />
        </section>
      ) : (
        <div className={styles.board}>
          <Chessboard fen={replayed.plies[current].fen} />
          <MoveList plies={replayed.plies} current={current} onSelect={select} />
        </div>
      )}

      <Link to="/">Back to games</Link>
    </article>
  );
}
```

Create `apps/web/src/features/games/pages/GameViewerPage.module.css`:

```css
.viewer {
  padding: 24px 0;
  text-align: left;
}

.board {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.unreplayable {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.movetext {
  font-family: var(--mono);
  font-size: 14px;
  white-space: pre-wrap;
  padding: 12px;
  background: var(--code-bg);
  border-radius: 4px;
  margin: 0;
}

.problem {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--accent-border);
  border-radius: 4px;
  background: var(--accent-bg);
}

.problem button {
  font: inherit;
  font-size: 15px;
  color: var(--text-h);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 6px 12px;
  cursor: pointer;
  flex-shrink: 0;
}

.problem button:hover {
  border-color: var(--accent-border);
}

.problem button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}
```

- [ ] **Step 4: Run the page test**

Run: `yarn test src/features/games/pages/GameViewerPage.test.tsx`
Expected: PASS — seven tests.

- [ ] **Step 5: Run everything**

Run: `yarn test && yarn lint && yarn build`
Expected: PASS, no warnings.

- [ ] **Step 6: Mutation-check**

1. Remove `key={state.game.id}` — no test may fail (the key guards a case only navigation reaches; `useReplay`'s bound is what the suite pins). Note this in your report, restore the key, and do not delete it.
2. Change `replayed.plies[current].fen` to `replayed.plies[0].fen` — the "position of the move that was clicked" test must fail.
3. Add a Retry button to the `not-found` branch — the "says the game is not here, and offers no retry" test must fail. Retry exists for a request that might succeed next time; a game that is not there will not appear on a second ask.

Restore all changes.

- [ ] **Step 7: Look at it**

Start the stack per the root README: `docker compose -f infra/docker-compose.yml up -d`, then `mvn -f services/core/pom.xml spring-boot:run`, then `yarn dev`. Import a game with the `curl` in the README's spirit, open the list, and click View.

Check: the board renders with pieces at a sensible size; clicking a move changes the position; the coordinates are legible; the move list does not wrap awkwardly beside the board; dark mode is not broken. Report what you saw. **If you cannot start the backend, say so plainly rather than implying you checked.**

- [ ] **Step 8: Commit**

```bash
git add src/features/games/pages
git commit -m "Game viewer page (#11)"
```

---

## Verification

From `apps/web`:

```bash
yarn test    # every suite
yarn lint    # oxlint, must be warning-free
yarn build   # tsc -b && vite build
```

Then the manual check in Task 9 Step 7, against a running backend.

## What This Plan Does Not Build

From the spec's out-of-scope list, so nobody adds them: URL-synced list filters · arrow-key navigation · board flipping · engine analysis · move annotations, variations or comments · PGN export · editing or entering moves · opening names beyond the stored ECO tag · the scoresheet review screen (#17) · generated API types (#27) · authentication (#25).
