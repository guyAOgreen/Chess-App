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
 * The effect depends on the request *path*, not on the query object. The path
 * is the request, so it is what actually changed; and being a string, an
 * equivalent query built fresh on every render does not restart the request.
 *
 * Every request carries an `AbortController`, and the cleanup aborts it. This
 * is what stops a slow response for filters the user has left behind from
 * landing last and repainting the table. An abort is not a failure — the
 * signal is checked before any state is set — so a cancelled request leaves
 * the state alone.
 *
 * A refetch keeps the current page visible and raises `refreshing` rather
 * than falling back to `loading`, so paging does not blank the table.
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
