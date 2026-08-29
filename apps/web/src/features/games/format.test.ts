import { describe, expect, it } from 'vitest';
import { EM_DASH, orDash, resultLabel, sideLabel, sourceLabel } from './format';
import { GAME_RESULTS, GAME_SOURCES } from './types/game';

describe('resultLabel', () => {
  it('renders the PGN token, which is what a chess player reads', () => {
    expect(resultLabel('WHITE_WON')).toBe('1-0');
    expect(resultLabel('BLACK_WON')).toBe('0-1');
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
    expect(sourceLabel('PGN_IMPORT')).toBe('PGN import');
    expect(sourceLabel('CHESS_COM')).toBe('Chess.com');
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
    expect(orDash(null)).toBe(EM_DASH);
    expect(orDash('Hastings')).toBe('Hastings');
  });
});
