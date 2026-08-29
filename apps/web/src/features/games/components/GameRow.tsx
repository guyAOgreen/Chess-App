import { orDash, resultLabel, sideLabel, sourceLabel } from '../format';
import type { GameSummary } from '../types/game';

/**
 * One game as a table row.
 *
 * Its own component because
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
