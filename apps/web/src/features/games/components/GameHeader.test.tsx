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

  it('shows the metadata that was recorded', () => {
    render(<GameHeader game={COMPLETE} />);

    expect(screen.getByText(/World Championship/)).toBeInTheDocument();
    expect(screen.getByText(/Dubai/)).toBeInTheDocument();
    expect(screen.getByText('2021-12-03')).toBeInTheDocument();
    expect(screen.getByText('C88')).toBeInTheDocument();
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
