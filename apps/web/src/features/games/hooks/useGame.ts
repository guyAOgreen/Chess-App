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
  // Holds only the request arms. `invalid-id` is never stored here — it is a
  // pure function of `path` and is derived below, not tracked as separate
  // state that could disagree with the id currently in hand.
  const [fetched, setFetched] = useState<GameState>({ kind: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (path === null) {
      return;
    }

    const controller = new AbortController();
    // oxlint-disable-next-line react/set-state-in-effect -- whether to reset to loading depends on the previously settled fetch state, which is not derivable during render
    setFetched((current) => (current.kind === 'loading' ? current : { kind: 'loading' }));

    fetchGame(path, controller.signal)
      .then((game) => {
        if (!controller.signal.aborted) {
          setFetched({ kind: 'ready', game });
        }
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setFetched(
          error instanceof GameNotFound
            ? { kind: 'not-found' }
            : { kind: 'failed', message: messageOf(error) },
        );
      });

    return () => controller.abort();
  }, [path, attempt]);

  const state: GameState = path === null ? { kind: 'invalid-id' } : fetched;

  const retry = useCallback(() => setAttempt((previous) => previous + 1), []);

  return { state, retry };
}
