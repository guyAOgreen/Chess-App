import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GamesPage } from './GamesPage';
import type { GamePage, GameSummary } from '../types/game';

function game(id: string, event: string): GameSummary {
  return {
    id,
    white: { playerId: 'w', name: 'Carlsen, M', rating: 2839 },
    black: { playerId: 'b', name: 'Nepomniachtchi, I', rating: 2792 },
    event,
    site: 'Dubai',
    round: '6',
    playedOn: '2021-12-03',
    result: 'WHITE_WON',
    eco: 'C88',
    source: 'PGN_IMPORT',
  };
}

function page(games: GameSummary[]): GamePage {
  return {
    content: games,
    page: 0,
    size: 25,
    totalElements: games.length,
    totalPages: games.length === 0 ? 0 : 1,
  };
}

function respondWith(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as unknown as Response;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('GamesPage', () => {
  it('lists the games it loaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respondWith(page([game('1', 'Hastings')]))));

    render(<GamesPage />);

    expect(screen.getByText(/loading games/i)).toBeInTheDocument();
    expect(await screen.findByText('Hastings')).toBeInTheDocument();
    expect(screen.getByText('Carlsen, M (2839)')).toBeInTheDocument();
  });

  it('sends the event term once typing settles, asking for the first page', async () => {
    const fetchStub = vi.fn().mockResolvedValue(respondWith(page([game('1', 'Hastings')])));
    vi.stubGlobal('fetch', fetchStub);

    render(<GamesPage />);
    await screen.findByText('Hastings');

    await userEvent.type(screen.getByLabelText(/event/i), 'Hast');

    await waitFor(() => {
      const paths = fetchStub.mock.calls.map((call) => call[0] as string);
      expect(paths.some((path) => path.includes('event=Hast') && path.includes('page=0'))).toBe(
        true,
      );
    });
  });

  it('says the database is empty when nothing is filtered, and keeps the filters usable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respondWith(page([]))));

    render(<GamesPage />);

    expect(await screen.findByText(/no games yet/i)).toBeInTheDocument();
    // The empty-result branch must never hand GameTable an empty array — it
    // renders a headers-only table with nothing to say anything is wrong.
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    // The filter form has to stay mounted and usable, not just present.
    expect(screen.getByLabelText(/event/i)).toBeEnabled();
    expect(screen.getByLabelText(/result/i)).toBeEnabled();
  });

  it('says the filters matched nothing when a filter is set, and lets the user filter back out', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respondWith(page([]))));

    render(<GamesPage />);
    await screen.findByText(/no games yet/i);

    await userEvent.selectOptions(screen.getByLabelText(/result/i), 'DRAW');

    expect(await screen.findByText(/no games match these filters/i)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    // Still usable here too, in both directions: the form must be able to
    // clear the filter and return to the unfiltered empty state.
    expect(screen.getByLabelText(/event/i)).toBeEnabled();

    await userEvent.click(screen.getByRole('button', { name: /clear filters/i }));

    expect(await screen.findByText(/no games yet/i)).toBeInTheDocument();
  });

  it('keeps the filters usable when the request fails, and retries', async () => {
    const fetchStub = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(respondWith(page([game('1', 'Hastings')])));
    vi.stubGlobal('fetch', fetchStub);

    render(<GamesPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/failed to fetch/i);
    expect(screen.getByLabelText(/event/i)).toBeEnabled();

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByText('Hastings')).toBeInTheDocument();
  });

  it('marks the table busy while a refresh is in flight, and clears it once the new page lands', async () => {
    let resolveSecond: (value: Response) => void = () => {
      throw new Error('resolveSecond called before assignment');
    };
    const second = new Promise<Response>((resolve) => {
      resolveSecond = resolve;
    });
    const fetchStub = vi
      .fn()
      .mockResolvedValueOnce(respondWith(page([game('1', 'Hastings')])))
      .mockImplementationOnce(() => second);
    vi.stubGlobal('fetch', fetchStub);

    render(<GamesPage />);
    await screen.findByText('Hastings');

    const wrapper = screen.getByRole('table').closest('[aria-busy]');
    expect(wrapper).toHaveAttribute('aria-busy', 'false');

    await userEvent.selectOptions(screen.getByLabelText(/result/i), 'DRAW');

    await waitFor(() => {
      expect(wrapper).toHaveAttribute('aria-busy', 'true');
    });
    // The stale page must stay on screen while the refresh is pending, not blank out.
    expect(screen.getByText('Hastings')).toBeInTheDocument();

    resolveSecond(respondWith(page([game('2', 'London')])));

    await waitFor(() => {
      expect(wrapper).toHaveAttribute('aria-busy', 'false');
    });
    expect(await screen.findByText('London')).toBeInTheDocument();
  });
});
