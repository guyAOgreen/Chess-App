const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];

export const PIECE_NAMES: Record<string, string> = {
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
