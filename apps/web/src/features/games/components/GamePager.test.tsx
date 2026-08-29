import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { GamePager } from './GamePager';

describe('GamePager', () => {
  it('counts pages from one, because the API counts them from zero', () => {
    render(<GamePager page={1} totalElements={168} totalPages={7} onPageChange={vi.fn()} />);

    expect(screen.getByText(/page 2 of 7/i)).toBeInTheDocument();
    expect(screen.getByText(/168 games/i)).toBeInTheDocument();
  });

  it('cannot go back from the first page', () => {
    render(<GamePager page={0} totalElements={168} totalPages={7} onPageChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled();
  });

  it('cannot go forward from the last page', () => {
    render(<GamePager page={6} totalElements={168} totalPages={7} onPageChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: /previous/i })).toBeEnabled();
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
  });

  it('does not disable previous one page early', () => {
    // Guards against an off-by-one boundary such as `page <= 1`: previous
    // must stay enabled on the page right after the first one.
    render(<GamePager page={1} totalElements={168} totalPages={7} onPageChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: /previous/i })).toBeEnabled();
  });

  it('does not disable next one page early', () => {
    // Guards against an off-by-one boundary such as `page + 2 >= totalPages`:
    // next must stay enabled on the page right before the last one.
    render(<GamePager page={5} totalElements={168} totalPages={7} onPageChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled();
  });

  it('moves a page at a time', async () => {
    const onPageChange = vi.fn();
    render(<GamePager page={3} totalElements={168} totalPages={7} onPageChange={onPageChange} />);

    await userEvent.click(screen.getByRole('button', { name: /next/i }));
    expect(onPageChange).toHaveBeenCalledWith(4);

    await userEvent.click(screen.getByRole('button', { name: /previous/i }));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it('says "1 game" rather than "1 games"', () => {
    render(<GamePager page={0} totalElements={1} totalPages={1} onPageChange={vi.fn()} />);

    expect(screen.getByText(/1 game\b/i)).toBeInTheDocument();
  });
});
