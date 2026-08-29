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
 * The wire parameter names are written out here rather than spread from the
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
      throw new GamesRequestFailed(`Could not reach the server (${response.message}).`);
  }
}
