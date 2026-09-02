import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchGame, fetchGames, gamePath, gamesPath, GameNotFound, GamesRequestFailed } from './games';
import type { Game, GamePage, GamesQuery } from '../types/game';

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

function nonJsonResponse(status: number): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      throw new SyntaxError('Unexpected token <');
    },
  } as unknown as Response;
}

async function failureOf(path: string): Promise<unknown> {
  return fetchGames(path).catch((error: unknown) => error);
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

  it('never sends sort, direction or size even when the query object carries them', () => {
    // The backend defaults to PLAYED_ON DESC and size 25; sending the only value
    // GameSort has would say nothing. See spec decision 10.
    //
    // gamesPath's parameter type has no sort/direction/size fields, so a query
    // built from GamesQuery could never carry them — that would make this
    // assertion trivially true. Cast an object that *does* carry them so the
    // test actually exercises gamesPath's allowlist behaviour: it must only
    // forward the fields it names, not everything on the object it receives.
    const path = gamesPath({
      page: 0,
      sort: 'PLAYED_ON',
      direction: 'DESC',
      size: 25,
    } as unknown as GamesQuery);

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

  it('fails when the server rejects the request, naming the status in the message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ title: 'Bad Request' }, 400)));

    const failure = await failureOf('/api/games?page=0');

    expect(failure).toBeInstanceOf(GamesRequestFailed);
    expect((failure as Error).message).toMatch(/\(400\)/);
  });

  it('fails when a non-2xx response is not JSON, distinguishing it from a rejected request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(nonJsonResponse(502)));

    const failure = await failureOf('/api/games?page=0');

    expect(failure).toBeInstanceOf(GamesRequestFailed);
    expect((failure as Error).message).toMatch(/502/);
    expect((failure as Error).message).toMatch(/not JSON/);
  });

  it('fails when a 200 response is not JSON — e.g. a proxy serving its index.html', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(nonJsonResponse(200)));

    const failure = await failureOf('/api/games?page=0');

    expect(failure).toBeInstanceOf(GamesRequestFailed);
    expect((failure as Error).message).toMatch(/200/);
    expect((failure as Error).message).toMatch(/not JSON/);
  });

  it('fails with a written explanation, keeping the transport message as the cause', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const failure = await failureOf('/api/games?page=0');

    expect(failure).toBeInstanceOf(GamesRequestFailed);
    // A user with a stopped backend must not see the bare browser string
    // ("Failed to fetch" / "Load failed") alone on the page — but the cause
    // must still be there for anyone diagnosing the failure.
    expect((failure as Error).message).not.toBe('Failed to fetch');
    expect((failure as Error).message).toMatch(/reach the server/);
    expect((failure as Error).message).toContain('Failed to fetch');
  });

  it('passes the abort signal through', async () => {
    const fetchStub = vi.fn().mockResolvedValue(jsonResponse(EMPTY_PAGE));
    vi.stubGlobal('fetch', fetchStub);
    const controller = new AbortController();

    await fetchGames('/api/games?page=0', controller.signal);

    expect(fetchStub.mock.calls[0][1].signal).toBe(controller.signal);
  });
});

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
