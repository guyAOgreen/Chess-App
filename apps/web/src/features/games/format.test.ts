import { describe, expect, it } from 'vitest';
import { orDash, resultLabel, sideLabel, sourceLabel, spokenResultLabel, viewLinkLabel } from './format';
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

describe('spokenResultLabel', () => {
  it('renders the spoken form, distinct from the visual token', () => {
    // Every value pinned explicitly, not a loop over truthiness: this project
    // has been bitten by a loop that would pass with two labels swapped, or
    // with a spoken label that just echoes the visual one back unread.
    expect(spokenResultLabel('WHITE_WON')).toBe('White won');
    expect(spokenResultLabel('BLACK_WON')).toBe('Black won');
    expect(spokenResultLabel('DRAW')).toBe('Draw');
    expect(spokenResultLabel('UNFINISHED')).toBe('Unfinished');
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

describe('viewLinkLabel', () => {
  it('starts with the visible link text, so voice control matching "View" still works', () => {
    // A plausible "less verbose" edit reorders this to the names first — that
    // would silently break voice control, so the start of the string is
    // asserted explicitly rather than just checking the names are present.
    expect(viewLinkLabel('Carlsen, M', 'Nepomniachtchi, I', null)).toMatch(/^View /);
  });

  it('names both sides when no date is recorded', () => {
    expect(viewLinkLabel('Carlsen, M', 'Nepomniachtchi, I', null)).toBe(
      'View Carlsen, M versus Nepomniachtchi, I',
    );
  });

  it('appends the date when one is recorded, to tell repeated pairings apart', () => {
    // Two rounds of the same match-up otherwise share one accessible name,
    // returning the link list to the ambiguity the label exists to fix.
    expect(viewLinkLabel('Carlsen, M', 'Nepomniachtchi, I', '2021-12-03')).toBe(
      'View Carlsen, M versus Nepomniachtchi, I, 2021-12-03',
    );
  });

  it('never reads an em dash for a missing date — silence, not noise, in a spoken name', () => {
    expect(viewLinkLabel('Carlsen, M', 'Nepomniachtchi, I', null)).not.toContain('—');
  });
});
