import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GameHeader } from './GameHeader';
import type { Game } from '../types/game';

const COMPLETE: Game = {
  id: '1',
  white: { playerId: 'w', name: 'Carlsen, M', rating: 2839 },
  black: { playerId: 'b', name: 'Nepomniachtchi, I', rating: 2792 },
  event: 'World Championship',
  site: 'Dubai',
  round: '6',
  playedOn: '2021-12-03',
  result: 'WHITE_WON',
  eco: 'C88',
  source: 'PGN_IMPORT',
  movetext: '1. e4 e5',
};

const SPARSE: Game = {
  ...COMPLETE,
  white: { playerId: 'w', name: 'Green, G', rating: null },
  black: { playerId: 'b', name: 'Opponent, O', rating: null },
  event: null,
  site: null,
  round: null,
  playedOn: null,
  eco: null,
  result: 'UNFINISHED',
};

/**
 * The value shown under a given `<dt>` label, read via its containing `<div>`
 * rather than a bare `getByText` on the value. Metadata is rendered as several
 * distinct strings; asserting only that a value is present anywhere in the
 * document (or only counting em dashes) is satisfied by the right values under
 * the wrong labels — e.g. Event and Site swapped. Scoping through the label
 * pins each value to its own field.
 */
function fieldValue(label: string): string | null {
  const dt = screen.getByText(label);
  const container = dt.closest('div');
  return container?.querySelector('dd')?.textContent ?? null;
}

describe('GameHeader', () => {
  it('names both players with their game-time ratings', () => {
    render(<GameHeader game={COMPLETE} />);

    expect(screen.getByText('Carlsen, M (2839)')).toBeInTheDocument();
    expect(screen.getByText('Nepomniachtchi, I (2792)')).toBeInTheDocument();
  });

  it('shows the result as its display token', () => {
    render(<GameHeader game={COMPLETE} />);

    expect(screen.getByText('1-0')).toBeInTheDocument();
  });

  it('exposes a clean accessible name for the heading, independent of its visual layout', () => {
    // The heading renders three adjacent spans with no textual separator
    // between them (the CSS gap is visual only), so its default accessible
    // name is a run-on string. An explicit aria-label is what a screen reader
    // actually announces.
    render(<GameHeader game={COMPLETE} />);

    expect(screen.getByRole('heading', { level: 2 })).toHaveAccessibleName(
      'Carlsen, M (2839) versus Nepomniachtchi, I (2792), White won',
    );
  });

  it('speaks an unfinished result as a word, not the bare "*" a screen reader cannot render', () => {
    // The visible span still shows resultLabel's '*' — this only pins the
    // accessible name, which must use spokenResultLabel instead.
    render(<GameHeader game={SPARSE} />);

    expect(screen.getByRole('heading', { level: 2 })).toHaveAccessibleName(
      'Green, G versus Opponent, O, Unfinished',
    );
  });

  it('shows each piece of metadata against its own label, not just present somewhere', () => {
    render(<GameHeader game={COMPLETE} />);

    // Every field has a distinct, non-null value here, so a value rendered
    // under the wrong label (e.g. Event and Site swapped) is caught.
    expect(fieldValue('Event')).toBe('World Championship');
    expect(fieldValue('Site')).toBe('Dubai');
    expect(fieldValue('Round')).toBe('6');
    expect(fieldValue('Date')).toBe('2021-12-03');
    expect(fieldValue('ECO')).toBe('C88');
    expect(fieldValue('Source')).toBe('PGN import');
  });

  it('renders absent metadata as an em dash rather than an empty gap', () => {
    render(<GameHeader game={SPARSE} />);

    // event, site, round, date and ECO were all absent.
    expect(screen.getAllByText('—')).toHaveLength(5);
  });

  it('omits the parentheses for a player with no recorded rating', () => {
    render(<GameHeader game={SPARSE} />);

    expect(screen.getByText('Green, G')).toBeInTheDocument();
  });
});
