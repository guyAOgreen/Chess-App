/**
 * One position in a replayed game.
 *
 * Index 0 is the initial position and is always present, which is why the
 * remaining fields are nullable. Without it there is no honest value for "which
 * ply is selected" before the first move, and every consumer needs a branch for
 * it.
 */
export interface Ply {
  index: number;
  /** 1-based; 0 for the initial position. */
  moveNumber: number;
  colour: 'white' | 'black' | null;
  san: string | null;
  fen: string;
}
