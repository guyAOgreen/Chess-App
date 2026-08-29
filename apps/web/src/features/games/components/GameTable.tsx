import { GameRow } from './GameRow';
import styles from './GameTable.module.css';
import type { GameSummary } from '../types/game';

/**
 * The list as a table. Takes a non-empty list: "no games yet" and "no games match
 * these filters" are different statements about the page's state, and telling them
 * apart needs to know whether a filter is set, which the page knows and this does
 * not.
 */
export function GameTable({ games }: { games: GameSummary[] }) {
  return (
    <table className={styles.table}>
      <caption className={styles.caption}>Games</caption>
      <thead>
        <tr>
          <th scope="col">White</th>
          <th scope="col">Black</th>
          <th scope="col">Result</th>
          <th scope="col">Date</th>
          <th scope="col">Event</th>
          <th scope="col">Site</th>
          <th scope="col">Round</th>
          <th scope="col">ECO</th>
          <th scope="col">Source</th>
        </tr>
      </thead>
      <tbody>
        {games.map((game) => (
          <GameRow key={game.id} game={game} />
        ))}
      </tbody>
    </table>
  );
}
