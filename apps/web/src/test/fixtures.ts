/**
 * Shared test fixtures: `fetch` response stubs, and builders for the games
 * contract in `features/games/types/game.ts`.
 *
 * The builders take partial overrides so a test states only the fields it
 * cares about. Their defaults describe a complete game with every optional
 * field set; a test that needs a field to be absent should say so explicitly.
 */

import type { Game, GamePage, GameSummary } from '../features/games/types/game';

/** A `fetch` response carrying `body` as JSON. `ok` follows the status, as on a real `Response`. */
export function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

/** What `nonJsonResponse`'s `json()` rejects with — the browser's message for parsing an HTML page. */
export const NON_JSON_MESSAGE = 'Unexpected token < in JSON at position 0';

/** A `fetch` response whose body is not JSON — e.g. a proxy serving its index.html. */
export function nonJsonResponse(status: number): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      throw new SyntaxError(NON_JSON_MESSAGE);
    },
  } as unknown as Response;
}

/** A row of the games list. */
export function aGameSummary(overrides: Partial<GameSummary> = {}): GameSummary {
  return {
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
    ...overrides,
  };
}

/** A game as the detail endpoint returns it: a summary plus its moves. */
export function aGame(overrides: Partial<Game> = {}): Game {
  return { ...aGameSummary(), movetext: '1. e4 e5', ...overrides };
}

/** A page of the games list holding `games`, and only those unless `overrides` says otherwise. */
export function aPage(games: GameSummary[], overrides: Partial<GamePage> = {}): GamePage {
  return {
    content: games,
    page: 0,
    size: 25,
    totalElements: games.length,
    totalPages: games.length === 0 ? 0 : 1,
    ...overrides,
  };
}
