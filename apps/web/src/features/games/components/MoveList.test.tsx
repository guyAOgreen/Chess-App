import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MoveList } from './MoveList';
import type { Ply } from '../types/ply';

const PLIES: Ply[] = [
  { index: 0, moveNumber: 0, colour: null, san: null, fen: 'initial' },
  { index: 1, moveNumber: 1, colour: 'white', san: 'e4', fen: 'f1' },
  { index: 2, moveNumber: 1, colour: 'black', san: 'e5', fen: 'f2' },
  { index: 3, moveNumber: 2, colour: 'white', san: 'Nf3', fen: 'f3' },
  { index: 4, moveNumber: 2, colour: 'black', san: 'Nc6', fen: 'f4' },
  { index: 5, moveNumber: 3, colour: 'white', san: 'Bb5', fen: 'f5' },
];

describe('MoveList', () => {
  it('pairs the moves by move number, white before black', () => {
    render(<MoveList plies={PLIES} current={0} onSelect={vi.fn()} />);

    const rows = screen.getAllByRole('row');
    expect(within(rows[0]).getByText('1')).toBeInTheDocument();
    const cells = within(rows[0]).getAllByRole('cell');
    expect(within(cells[0]).getByRole('button', { name: 'e4' })).toBeInTheDocument();
    expect(within(cells[1]).getByRole('button', { name: 'e5' })).toBeInTheDocument();
  });

  it('leaves the black cell empty when the game ends on a white move', () => {
    render(<MoveList plies={PLIES} current={0} onSelect={vi.fn()} />);

    const rows = screen.getAllByRole('row');
    expect(within(rows[2]).getByRole('button', { name: 'Bb5' })).toBeInTheDocument();
    expect(within(rows[2]).getAllByRole('button')).toHaveLength(1);
  });

  it('offers the initial position, so the start is reachable', () => {
    render(<MoveList plies={PLIES} current={3} onSelect={vi.fn()} />);

    expect(screen.getByRole('button', { name: /start/i })).toBeInTheDocument();
  });

  it('marks the current ply for a screen reader, not by colour alone', () => {
    render(<MoveList plies={PLIES} current={3} onSelect={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Nf3' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('button', { name: 'e4' })).not.toHaveAttribute('aria-current');
  });

  it('marks the start button as current when viewing the initial position', () => {
    render(<MoveList plies={PLIES} current={0} onSelect={vi.fn()} />);

    expect(screen.getByRole('button', { name: /start/i })).toHaveAttribute(
      'aria-current',
      'true',
    );
    expect(screen.getByRole('button', { name: 'e4' })).not.toHaveAttribute('aria-current');
  });

  it('raises the index of the clicked ply', async () => {
    const onSelect = vi.fn();
    render(<MoveList plies={PLIES} current={0} onSelect={onSelect} />);

    await userEvent.click(screen.getByRole('button', { name: 'Nc6' }));

    expect(onSelect).toHaveBeenCalledExactlyOnceWith(4);
  });

  it('raises 0 for the initial position', async () => {
    const onSelect = vi.fn();
    render(<MoveList plies={PLIES} current={3} onSelect={onSelect} />);

    await userEvent.click(screen.getByRole('button', { name: /start/i }));

    expect(onSelect).toHaveBeenCalledExactlyOnceWith(0);
  });
});
