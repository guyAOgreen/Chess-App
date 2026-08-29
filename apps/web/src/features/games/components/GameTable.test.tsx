import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GameTable } from './GameTable';
import type { GameSummary } from '../types/game';

const COMPLETE: GameSummary = {
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
};

const SPARSE: GameSummary = {
  id: '2',
  white: { playerId: 'w', name: 'Green, G', rating: null },
  black: { playerId: 'b', name: 'Opponent, O', rating: null },
  event: null,
  site: null,
  round: null,
  playedOn: null,
  result: 'UNFINISHED',
  eco: null,
  source: 'PERSONAL',
};

describe('GameTable', () => {
  it('has an accessible name, so a screen reader announces what the table lists', () => {
    render(<GameTable games={[COMPLETE]} />);

    expect(screen.getByRole('table', { name: /games/i })).toBeInTheDocument();
  });

  it('labels every column, in order, with a scope="col" header', () => {
    render(<GameTable games={[COMPLETE]} />);

    const headers = screen.getAllByRole('columnheader');
    expect(headers.map((header) => header.textContent)).toEqual([
      'White',
      'Black',
      'Result',
      'Date',
      'Event',
      'Site',
      'Round',
      'ECO',
      'Source',
    ]);
    for (const header of headers) {
      expect(header).toHaveAttribute('scope', 'col');
    }
  });

  it('renders a row per game, in the order given', () => {
    render(<GameTable games={[COMPLETE, SPARSE]} />);

    const rows = screen.getAllByRole('row').slice(1); // drop the header row
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText(/Carlsen, M/)).toBeInTheDocument();
    expect(within(rows[1]).getByText(/Green, G/)).toBeInTheDocument();
  });

  it('shows each side with the rating recorded at the time', () => {
    render(<GameTable games={[COMPLETE]} />);

    expect(screen.getByText('Carlsen, M (2839)')).toBeInTheDocument();
    expect(screen.getByText('Nepomniachtchi, I (2792)')).toBeInTheDocument();
  });

  it('omits the parentheses for a side with no rating', () => {
    render(<GameTable games={[SPARSE]} />);

    expect(screen.getByText('Green, G')).toBeInTheDocument();
  });

  it('shows the result as its PGN token', () => {
    render(<GameTable games={[COMPLETE, SPARSE]} />);

    expect(screen.getByText('1-0')).toBeInTheDocument();
    expect(screen.getByText('*')).toBeInTheDocument();
  });

  it('renders absent metadata as an em dash in the date, event, site, round and ECO columns', () => {
    render(<GameTable games={[SPARSE]} />);

    const row = screen.getAllByRole('row')[1];
    const cells = within(row).getAllByRole('cell');
    // White, Black, Result, Date, Event, Site, Round, ECO, Source.
    expect(cells).toHaveLength(9);
    expect(cells[3]).toHaveTextContent('—');
    expect(cells[4]).toHaveTextContent('—');
    expect(cells[5]).toHaveTextContent('—');
    expect(cells[6]).toHaveTextContent('—');
    expect(cells[7]).toHaveTextContent('—');
    // The columns that are never absent must not also read as a dash.
    expect(cells[0]).not.toHaveTextContent('—');
    expect(cells[1]).not.toHaveTextContent('—');
    expect(cells[2]).not.toHaveTextContent('—');
    expect(cells[8]).not.toHaveTextContent('—');
  });

  it('shows the date exactly as the API reports it', () => {
    render(<GameTable games={[COMPLETE]} />);

    expect(screen.getByText('2021-12-03')).toBeInTheDocument();
  });

  it('humanises the source', () => {
    render(<GameTable games={[COMPLETE]} />);

    expect(screen.getByText('PGN import')).toBeInTheDocument();
  });

  it('places every field in its own column, in header order', () => {
    render(<GameTable games={[COMPLETE]} />);

    const row = screen.getAllByRole('row')[1];
    const cells = within(row).getAllByRole('cell');
    expect(cells.map((cell) => cell.textContent)).toEqual([
      'Carlsen, M (2839)',
      'Nepomniachtchi, I (2792)',
      '1-0',
      '2021-12-03',
      'World Championship',
      'Dubai',
      '6',
      'C88',
      'PGN import',
    ]);
  });
});
