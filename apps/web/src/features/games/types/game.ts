/**
 * The `GET /api/games` contract, hand-written.
 *
 * Mirrors `GameSummaryResponse` and `GamePageResponse` in the backend's
 * `com.chessapp.game.api` package. Nothing enforces the correspondence — that is
 * what [#27](https://github.com/guyAOgreen/Chess-App/issues/27) fixes, by
 * generating this file from an OpenAPI document and deleting it from source
 * control. Until then this is the single place the shape is spoken, so drift has
 * one place to be corrected.
 *
 * The arrays exist so a test can iterate every value; the types are derived
 * from them so the two cannot disagree.
 */

export const GAME_RESULTS = ['WHITE_WON', 'BLACK_WON', 'DRAW', 'UNFINISHED'] as const;
export type GameResult = (typeof GAME_RESULTS)[number];

export const GAME_SOURCES = [
  'PERSONAL',
  'CLUB',
  'PGN_IMPORT',
  'LICHESS',
  'CHESS_COM',
  'MEGA_DATABASE',
  'OTHER',
] as const;
export type GameSource = (typeof GAME_SOURCES)[number];

export type GameColour = 'WHITE' | 'BLACK';

/** One colour's share of the game. `name` is the game-time snapshot. */
export interface GameSide {
  playerId: string;
  name: string;
  rating: number | null;
}

/**
 * A game as a row of the list. No `movetext` — the detail endpoint carries that.
 *
 * Nullability follows the backend domain exactly: `white`, `black`, `result`
 * and `source` are always present, and the rest were optional in the document the
 * game was imported from.
 */
export interface GameSummary {
  id: string;
  white: GameSide;
  black: GameSide;
  event: string | null;
  site: string | null;
  round: string | null;
  playedOn: string | null;
  result: GameResult;
  eco: string | null;
  source: GameSource;
}

export interface GamePage {
  content: GameSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** What the filter form edits. */
export interface GameFilterValues {
  result?: GameResult;
  from?: string;
  to?: string;
  event?: string;
}

/**
 * What gets requested. `playerId` and `colour` have no control yet — the endpoint
 * takes a UUID and nothing turns a name into one until
 * [#21](https://github.com/guyAOgreen/Chess-App/issues/21), which adds the control
 * and nothing else.
 */
export interface GamesQuery extends GameFilterValues {
  playerId?: string;
  colour?: GameColour;
  page: number;
}
