import { Chess } from 'chess.js';
import { messageOf } from '../../lib/api';
import type { Ply } from './types/ply';

/** The standard starting position. Stored games carry no FEN tag, so every game
 * begins here. */
export const INITIAL_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

const INITIAL_PLY: Ply = Object.freeze({
  index: 0,
  moveNumber: 0,
  colour: null,
  san: null,
  fen: INITIAL_FEN,
});

export interface Replayed {
  /** Never empty: index 0 is always the initial position. */
  plies: Ply[];
  /** Set when the movetext could not be replayed at all. */
  error: string | null;
}

/**
 * The positions a game passes through.
 *
 * The only file in this application that imports chess.js — ADR 0001's pattern,
 * which wraps chesslib behind one boundary on the backend for the same reasons:
 * the constraint is enforced in one place, and the fallback stays real.
 *
 * Parsing goes through `loadPgn` rather than splitting SAN tokens by hand.
 * Movetext is a grammar, not a whitespace-delimited list — move numbers may sit
 * against their move, and SAN carries disambiguation, captures, promotion,
 * castling and check markers.
 *
 * Strict mode is deliberate. The input is machine-generated canonical SAN, so
 * anything chess.js considers non-strict is a divergence between it and chesslib,
 * and should surface as a visible error rather than a quietly wrong board.
 *
 * Unlike chesslib on the backend, chess.js does not require a termination
 * marker on the movetext, so none is appended here.
 *
 * Never throws: `loadPgn` raises on failure, and a viewer that blanked on a bad
 * game would be worse than one that says what went wrong.
 */
export function replay(movetext: string): Replayed {
  if (movetext.trim() === '') {
    return { plies: [INITIAL_PLY], error: null };
  }

  const chess = new Chess();
  try {
    chess.loadPgn(movetext, { strict: true });
  } catch (error: unknown) {
    return { plies: [INITIAL_PLY], error: messageOf(error) };
  }

  const moves = chess.history({ verbose: true });
  const plies: Ply[] = moves.map((move, i) => ({
    index: i + 1,
    moveNumber: Math.floor(i / 2) + 1,
    colour: i % 2 === 0 ? 'white' : 'black',
    san: move.san,
    fen: move.after,
  }));

  return { plies: [INITIAL_PLY, ...plies], error: null };
}
