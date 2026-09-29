import { useLayoutEffect, useRef } from 'react';
import styles from './MoveList.module.css';
import { scrollOffsetFor } from './moveScroll';
import type { Ply } from '../types/ply';

/** The selected move. The same attribute the highlight's CSS keys off, so the
 * scroll and the highlight can never disagree about which move is current. */
const CURRENT_MOVE = '[aria-current="true"]';

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
  const listRef = useRef<HTMLDivElement | null>(null);

  // The list has its own max-height and scrolls independently of the board, so a
  // selection made with the keyboard can land outside the visible window — the
  // board would move while the highlight stayed where it was.
  //
  // Only this container's own scrollTop is touched. `scrollIntoView` would be
  // shorter but it scrolls every scrollable ancestor including the document, and
  // its `block` option picks the alignment within each rather than which one
  // moves — so on the stacked layout it would push the board out of view to
  // bring a late move in. A layout effect rather than an effect, so the scroll
  // lands before paint instead of as a visible jump.
  useLayoutEffect(() => {
    const list = listRef.current;
    const move = list?.querySelector(CURRENT_MOVE);
    if (list === null || move === null || move === undefined) {
      return;
    }
    list.scrollTop += scrollOffsetFor(list.getBoundingClientRect(), move.getBoundingClientRect());
  }, [current]);

  return (
    <div className={styles.moves} ref={listRef}>
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
