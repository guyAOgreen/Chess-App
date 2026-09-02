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
