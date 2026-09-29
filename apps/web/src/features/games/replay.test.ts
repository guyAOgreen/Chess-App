import { describe, expect, it } from 'vitest';
import { INITIAL_FEN, replay } from './replay';

/** The placement field — the part of a FEN before the first space. */
function placement(fen: string): string {
  return fen.split(' ')[0];
}

describe('replay', () => {
  it('yields the initial position for empty movetext', () => {
    const { plies, error } = replay('');

    expect(error).toBeNull();
    expect(plies).toHaveLength(1);
    expect(plies[0]).toEqual({
      index: 0,
      moveNumber: 0,
      colour: null,
      san: null,
      fen: INITIAL_FEN,
    });
  });

  it('always begins at the initial position', () => {
    const { plies } = replay('1. e4 e5');

    expect(plies[0].san).toBeNull();
    expect(plies[0].fen).toBe(INITIAL_FEN);
  });

  it('produces the position after each ply', () => {
    const { plies, error } = replay('1. e4 e5 2. Nf3');

    expect(error).toBeNull();
    expect(plies).toHaveLength(4);
    // chess.js only includes the en passant target square when the side to move
    // can legally capture en passant (per its README); black has no pawn on d5
    // or f5 yet, so the field stays '-' rather than 'e3'.
    expect(plies[1].fen).toBe('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1');
    expect(plies.map((p) => p.san)).toEqual([null, 'e4', 'e5', 'Nf3']);
  });

  it('numbers moves and alternates colours', () => {
    const { plies } = replay('1. e4 e5 2. Nf3 Nc6');

    expect(plies.map((p) => p.moveNumber)).toEqual([0, 1, 1, 2, 2]);
    expect(plies.map((p) => p.colour)).toEqual([null, 'white', 'black', 'white', 'black']);
    expect(plies.map((p) => p.index)).toEqual([0, 1, 2, 3, 4]);
  });

  it('replays castling', () => {
    // 5. O-O puts the white king on g1 and the h1 rook on f1.
    const { plies, error } = replay('1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O');

    expect(error).toBeNull();
    expect(placement(plies.at(-1)!.fen)).toMatch(/RNBQ1RK1$/);
  });

  it('replays en passant', () => {
    const { plies, error } = replay('1. e4 d5 2. e5 f5 3. exf6');

    expect(error).toBeNull();
    expect(plies.at(-1)!.san).toBe('exf6');
    // The captured black f5 pawn is gone: rank 5 holds only the black d-pawn.
    expect(placement(plies.at(-1)!.fen).split('/')[3]).toBe('3p4');
  });

  it('replays promotion', () => {
    const { plies, error } = replay('1. e4 d5 2. exd5 c6 3. dxc6 Nf6 4. cxb7 Bd7 5. bxa8=Q');

    expect(error).toBeNull();
    expect(plies.at(-1)!.san).toBe('bxa8=Q');
    // a8 is now a white queen; c8 and g8 emptied by Bd7 and Nf6.
    expect(placement(plies.at(-1)!.fen).split('/')[0]).toBe('Qn1qkb1r');
  });

  it('replays a disambiguated move', () => {
    // Both black knights can reach d7, so SAN must name the file.
    const { plies, error } = replay('1. d4 Nf6 2. c4 e6 3. Nc3 d5 4. Nf3 Nbd7');

    expect(error).toBeNull();
    expect(plies.at(-1)!.san).toBe('Nbd7');
  });

  it('reports unparseable movetext instead of throwing', () => {
    const { plies, error } = replay('1. e4 e5 2. Qxf7');

    expect(error).not.toBeNull();
    expect(plies).toHaveLength(1);
    expect(plies[0].fen).toBe(INITIAL_FEN);
  });

  it('handles movetext with no terminal token, which is what the API returns', () => {
    // ValidatedMoves strips the result token, so nothing stored ends in 1-0.
    const { error } = replay('1. e4 e5');

    expect(error).toBeNull();
  });

  it('still replays movetext that does carry a trailing result token', () => {
    // The stored contract says this never happens, but replay must not choke
    // on it if it ever did: chess.js parses it fine on its own, and appending
    // a result token of our own would break exactly this case.
    const { plies, error } = replay('1. e4 e5 1-0');

    expect(error).toBeNull();
    expect(plies).toHaveLength(3);
  });
});
