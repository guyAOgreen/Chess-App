import type { GameResult, GameSide, GameSource } from './types/game';

/**
 * Absent metadata. An empty cell reads as a broken table; this reads as
 * "not recorded".
 */
export const EM_DASH = '—';

/**
 * The records are typed by the enum, so a value added to `GameResult` or
 * `GameSource` is a compile error here rather than a blank cell.
 */
const RESULT_LABELS: Record<GameResult, string> = {
  WHITE_WON: '1-0',
  BLACK_WON: '0-1',
  // Deliberately not the backend's pgnToken ('1/2-1/2', per GameResult.java) —
  // ½-½ is the display convention a chess player expects to read.
  DRAW: '½-½',
  UNFINISHED: '*',
};

const SOURCE_LABELS: Record<GameSource, string> = {
  PERSONAL: 'Personal',
  CLUB: 'Club',
  PGN_IMPORT: 'PGN import',
  LICHESS: 'Lichess',
  CHESS_COM: 'Chess.com',
  MEGA_DATABASE: 'Mega Database',
  OTHER: 'Other',
};

export function resultLabel(result: GameResult): string {
  return RESULT_LABELS[result];
}

export function sourceLabel(source: GameSource): string {
  return SOURCE_LABELS[source];
}

export function sideLabel(side: GameSide): string {
  return side.rating === null ? side.name : `${side.name} (${side.rating})`;
}

export function orDash(value: string | null): string {
  return value ?? EM_DASH;
}
