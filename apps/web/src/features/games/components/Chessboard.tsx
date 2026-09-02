import styles from './Chessboard.module.css';
import { PIECE_NAMES, squaresOf } from './squares';

function describe(piece: string): string {
  const colour = piece === piece.toUpperCase() ? 'white' : 'black';
  return `${colour} ${PIECE_NAMES[piece.toLowerCase()]}`;
}

function source(piece: string): string {
  const colour = piece === piece.toUpperCase() ? 'w' : 'b';
  return `/pieces/${colour}${piece.toLowerCase()}.svg`;
}

/**
 * A position, drawn.
 *
 * Takes a FEN string and nothing else — no game, no chess.js, no fetching. That
 * is what lets #17 hand it a position from a half-recognised scoresheet that is
 * not yet a legal game.
 *
 * Only the placement field is read, which is why this component needs no chess
 * library: expanding it is a string operation.
 */
export function Chessboard({ fen }: { fen: string }) {
  const squares = squaresOf(fen.split(' ')[0]);

  if (squares === null) {
    return <p className={styles.unreadable}>This position could not be read.</p>;
  }

  return (
    <div className={styles.board} role="group" aria-label="Chess position">
      {squares.map((square) => (
        <div
          key={square.name}
          className={square.light ? styles.light : styles.dark}
          {...(square.piece !== null
            ? { role: 'img', 'aria-label': `${square.name}, ${describe(square.piece)}` }
            : {})}
        >
          {square.piece !== null && <img src={source(square.piece)} alt="" />}
          {square.name[1] === '1' && (
            <span className={styles.file} aria-hidden="true">
              {square.name[0]}
            </span>
          )}
          {square.name[0] === 'a' && (
            <span className={styles.rank} aria-hidden="true">
              {square.name[1]}
            </span>
          )}
        </div>
      ))}
    </div>
  );
}
