import styles from './Chessboard.module.css';

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];

const PIECE_NAMES: Record<string, string> = {
  p: 'pawn',
  n: 'knight',
  b: 'bishop',
  r: 'rook',
  q: 'queen',
  k: 'king',
};

export interface Square {
  name: string;
  /** The FEN letter, case carrying colour, or null for an empty square. */
  piece: string | null;
  light: boolean;
}

/**
 * A FEN's placement field as 64 squares, a8 first and h1 last — reading order.
 *
 * Returns null rather than a partial board when the placement does not describe
 * eight ranks of eight squares. A board drawn from a bad placement is shifted
 * rather than obviously broken, which is the worst of both outcomes.
 */
// oxlint-disable-next-line react/only-export-components -- squaresOf is exported deliberately, for its own unit tests (see task interface contract)
export function squaresOf(placement: string): Square[] | null {
  const ranks = placement.split('/');
  if (ranks.length !== 8) {
    return null;
  }

  const squares: Square[] = [];
  for (let rankIndex = 0; rankIndex < 8; rankIndex++) {
    const rankNumber = 8 - rankIndex;
    let file = 0;

    for (const character of ranks[rankIndex]) {
      if (character >= '1' && character <= '8') {
        const run = Number(character);
        for (let i = 0; i < run; i++) {
          squares.push(square(file + i, rankNumber, null));
        }
        file += run;
      } else if (PIECE_NAMES[character.toLowerCase()] !== undefined) {
        squares.push(square(file, rankNumber, character));
        file += 1;
      } else {
        return null;
      }
    }

    if (file !== 8) {
      return null;
    }
  }

  return squares;
}

function square(file: number, rankNumber: number, piece: string | null): Square {
  return {
    name: `${FILES[file]}${rankNumber}`,
    piece,
    // a1 (file 0, rank 1) sums to 1 and is dark; h1 sums to 8 and is light.
    light: (file + rankNumber) % 2 === 0,
  };
}

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
          {square.name[1] === '1' && <span className={styles.file}>{square.name[0]}</span>}
          {square.name[0] === 'a' && <span className={styles.rank}>{square.name[1]}</span>}
        </div>
      ))}
    </div>
  );
}
