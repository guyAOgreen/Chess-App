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
      expect(result.current.state.page.content[0].id).toBe('1');
      expect(result.current.state.refreshing).toBe(false);
    } else {
      throw new Error(`expected ready, got ${result.current.state.kind}`);
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
      expect(result.current.state.kind).toBe('ready');
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
    // Give the (already-resolved) slow response's `.then` a turn of the
    // microtask queue, so a missing abort guard has a chance to act before
    // the assertion below runs.
    await new Promise((resolve) => setTimeout(resolve, 0));
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(result.current.state.kind).toBe('ready');
    if (result.current.state.kind === 'ready') {
      expect(result.current.state.page.content).toHaveLength(1);
      expect(result.current.state.page.content[0].id).toBe('2');
      expect(result.current.state.page.content[0].event).toBe('Wijk aan Zee');
    }
  });

  it('reports a failure, and retries on demand with a real second request', async () => {
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

    // A no-op `retry` would leave the call count at one forever; asserting on
    // the count (not just the eventual state) is what catches that mutant.
    await waitFor(() => expect(fetchStub).toHaveBeenCalledTimes(2));
    expect(fetchStub.mock.calls[1][0]).toBe(fetchStub.mock.calls[0][0]);
    await waitFor(() => expect(result.current.state.kind).toBe('ready'));
  });

  it('aborts the in-flight request when the query changes', async () => {
    // The mechanism the superseded-response test relies on: without a real
    // `abort()` call in the effect's cleanup, there is no signal for the
    // hook to check, and that test would be relying on nothing.
    const abortSpy = vi.fn();
    const fetchStub = vi.fn().mockImplementation((_path: string, init: { signal: AbortSignal }) => {
      init.signal.addEventListener('abort', abortSpy);
      return new Promise(() => {
        // Never settles: only whether `abort` fired matters here.
      });
    });
    vi.stubGlobal('fetch', fetchStub);

    const { rerender } = renderHook(({ query }) => useGames(query), {
      initialProps: { query: { event: 'Hastings', page: 0 } as GamesQuery },
    });
    await waitFor(() => expect(fetchStub).toHaveBeenCalledTimes(1));

    rerender({ query: { event: 'Wijk aan Zee', page: 0 } as GamesQuery });
    await waitFor(() => expect(fetchStub).toHaveBeenCalledTimes(2));

    expect(abortSpy).toHaveBeenCalledTimes(1);
  });

  it('does not report an aborted request as a failure', async () => {
    // Deliberately does not unmount. Unmounting tears down the render tree,
    // so a setState call the hook makes afterwards is discarded by React
    // regardless of whether the guard the hook is supposed to have is
    // present — which would make the assertion pass whether or not the
    // guard exists, proving nothing. Staying mounted and rerendering with a
    // new query keeps the component alive and able to re-render, so if the
    // superseded request's rejection is (wrongly) treated as a failure,
    // that state change is actually observable here.
    const fetchStub = vi
      .fn()
      .mockImplementationOnce(
        (_path: string, init: { signal: AbortSignal }) =>
          new Promise((_resolve, reject) => {
            init.signal.addEventListener('abort', () =>
              reject(new DOMException('Aborted', 'AbortError')),
            );
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise(() => {
            // The second (current) request is left unresolved on purpose: if
            // the first request's abort were mishandled as a failure, that
            // would be visible before this one ever answers.
          }),
      );
    vi.stubGlobal('fetch', fetchStub);

    const { result, rerender } = renderHook(({ query }) => useGames(query), {
      initialProps: { query: { event: 'Hastings', page: 0 } as GamesQuery },
    });
    await waitFor(() => expect(fetchStub).toHaveBeenCalledTimes(1));

    rerender({ query: { event: 'Wijk aan Zee', page: 0 } as GamesQuery });
    await waitFor(() => expect(fetchStub).toHaveBeenCalledTimes(2));

    // Let the aborted first request's rejection (and its `.catch`) run.
    await new Promise((resolve) => setTimeout(resolve, 0));
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(result.current.state.kind).toBe('loading');
  });
});
