import styles from './MoveList.module.css';
import type { Ply } from '../types/ply';

interface MoveRow {
  moveNumber: number;
  white: Ply | null;
  black: Ply | null;
}

/**
 * Plies grouped into scoresheet rows. Index 0 is the initial position and is not
 * a move, so it never appears in a row.
 */
function rowsOf(plies: Ply[]): MoveRow[] {
  const rows = new Map<number, MoveRow>();

  for (const ply of plies) {
    if (ply.colour === null) {
      continue;
    }
    const row = rows.get(ply.moveNumber) ?? {
      moveNumber: ply.moveNumber,
      white: null,
      black: null,
    };
    if (ply.colour === 'white') {
      row.white = ply;
    } else {
      row.black = ply;
    }
    rows.set(ply.moveNumber, row);
  }

  return [...rows.values()].sort((a, b) => a.moveNumber - b.moveNumber);
}

export interface MoveListProps {
  plies: Ply[];
  current: number;
  onSelect: (index: number) => void;
}

/**
 * The moves, laid out the way a scoresheet is: move number, White, Black. That
 * shape is not incidental — #17 puts a scoresheet image beside this component.
 *
 * Takes plies and an index, so it is decoupled from how the game was loaded.
 */
export function MoveList({ plies, current, onSelect }: MoveListProps) {
  return (
    <div className={styles.moves}>
      <button
        type="button"
        className={styles.start}
        onClick={() => onSelect(0)}
        {...(current === 0 ? { 'aria-current': 'true' as const } : {})}
      >
        Start
      </button>
      <table className={styles.table}>
        <caption className={styles.caption}>Moves</caption>
        <tbody>
          {rowsOf(plies).map((row) => (
            <tr key={row.moveNumber}>
              <th scope="row" className={styles.number}>
                {row.moveNumber}
              </th>
              <td>{row.white !== null && moveButton(row.white, current, onSelect)}</td>
              <td>{row.black !== null && moveButton(row.black, current, onSelect)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function moveButton(ply: Ply, current: number, onSelect: (index: number) => void) {
  return (
    <button
      type="button"
      className={styles.move}
      onClick={() => onSelect(ply.index)}
      {...(ply.index === current ? { 'aria-current': 'true' as const } : {})}
    >
      {ply.san}
    </button>
  );
}
