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
    const slow = {
      promise: null as unknown as Promise<Response>,
      resolve: null as unknown as (value: Response) => void,
    };
    slow.promise = new Promise((resolve) => {
      slow.resolve = resolve;
    });
    const fast = {
      promise: null as unknown as Promise<Response>,
      resolve: null as unknown as (value: Response) => void,
    };
    fast.promise = new Promise((resolve) => {
      fast.resolve = resolve;
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
