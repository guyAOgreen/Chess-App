import { describe, expect, it } from 'vitest';
import { orDash, resultLabel, sideLabel, sourceLabel } from './format';
import { GAME_RESULTS, GAME_SOURCES } from './types/game';

describe('resultLabel', () => {
  it('renders the display token a chess player reads, not the raw PGN token', () => {
    expect(resultLabel('WHITE_WON')).toBe('1-0');
    expect(resultLabel('BLACK_WON')).toBe('0-1');
    // Deliberately not the backend's pgnToken ('1/2-1/2', per GameResult.java) —
    // ½-½ is the display convention a chess player expects to read.
    expect(resultLabel('DRAW')).toBe('½-½');
    expect(resultLabel('UNFINISHED')).toBe('*');
  });

  it('has a label for every result, distinct from the raw enum constant', () => {
    // A value added to the backend enum fails here rather than rendering raw.
    // Checking inequality with the raw constant (not just truthiness) catches a
    // fallback that echoes the enum key back unlabelled.
    for (const result of GAME_RESULTS) {
      const label = resultLabel(result);
      expect(label).toBeTruthy();
      expect(label).not.toBe(result);
    }
  });
});

describe('sourceLabel', () => {
  it('humanises the enum constant', () => {
    // Every value pinned explicitly: a loop with only toBeTruthy() would not
    // catch two labels swapped with each other, or two sources sharing a label.
    expect(sourceLabel('PERSONAL')).toBe('Personal');
    expect(sourceLabel('CLUB')).toBe('Club');
    expect(sourceLabel('PGN_IMPORT')).toBe('PGN import');
    expect(sourceLabel('LICHESS')).toBe('Lichess');
    expect(sourceLabel('CHESS_COM')).toBe('Chess.com');
    expect(sourceLabel('MEGA_DATABASE')).toBe('Mega Database');
    expect(sourceLabel('OTHER')).toBe('Other');
  });

  it('has a label for every source, distinct from the raw enum constant', () => {
    for (const source of GAME_SOURCES) {
      const label = sourceLabel(source);
      expect(label).toBeTruthy();
      expect(label).not.toBe(source);
    }
  });
});

describe('sideLabel', () => {
  it('shows the rating when one was recorded', () => {
    expect(sideLabel({ playerId: 'a', name: 'Carlsen, M', rating: 2839 })).toBe(
      'Carlsen, M (2839)',
    );
  });

  it('omits the parentheses when no rating was recorded', () => {
    expect(sideLabel({ playerId: 'a', name: 'Carlsen, M', rating: null })).toBe('Carlsen, M');
  });
});

describe('orDash', () => {
  it('renders absent metadata as an em dash, because an empty cell reads as broken', () => {
    // Asserts the literal, not the EM_DASH constant under test — otherwise
    // setting EM_DASH to '' would still pass.
    expect(orDash(null)).toBe('—');
    expect(orDash('Hastings')).toBe('Hastings');
  });
});
