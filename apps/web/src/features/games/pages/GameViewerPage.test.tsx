import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GameViewerPage } from './GameViewerPage';
import type { Game } from '../types/game';

const ID = '11111111-1111-1111-1111-111111111111';

const A_GAME: Game = {
  id: ID,
  white: { playerId: 'w', name: 'Carlsen, M', rating: 2839 },
  black: { playerId: 'b', name: 'Nepomniachtchi, I', rating: 2792 },
  event: 'World Championship',
  site: 'Dubai',
  round: '6',
  playedOn: '2021-12-03',
  result: 'WHITE_WON',
  eco: 'C88',
  source: 'PGN_IMPORT',
  movetext: '1. e4 e5 2. Nf3 Nc6 3. Bb5',
};

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

function renderAt(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/games/${id}`]}>
      <Routes>
        <Route path="/games/:id" element={<GameViewerPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('GameViewerPage', () => {
  it('shows the game, its board and its moves', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);

    expect(await screen.findByText('Carlsen, M (2839)')).toBeInTheDocument();
    expect(screen.getByRole('group', { name: /position/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Nf3' })).toBeInTheDocument();
  });

  it('starts at the initial position', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);

    await screen.findByRole('group', { name: /position/i });
    // Every piece is still on its starting square.
    expect(screen.getByLabelText('e2, white pawn')).toBeInTheDocument();
  });

  it('shows the position of the move that was clicked', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);

    await userEvent.click(await screen.findByRole('button', { name: 'e4' }));

    expect(screen.getByLabelText('e4, white pawn')).toBeInTheDocument();
    expect(screen.queryByLabelText('e2, white pawn')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'e4' })).toHaveAttribute('aria-current', 'true');
  });

  it('steps through the moves with the arrow keys', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);
    await screen.findByRole('group', { name: /position/i });

    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByLabelText('e4, white pawn')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'e4' })).toHaveAttribute('aria-current', 'true');

    await userEvent.keyboard('{ArrowLeft}');
    expect(screen.getByLabelText('e2, white pawn')).toBeInTheDocument();
  });

  it('carries on with the arrows from a move that was clicked', async () => {
    // The realistic flow: click into the middle of the game, then step. The
    // keys read the selection the click made, not the one the page loaded with.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);

    await userEvent.click(await screen.findByRole('button', { name: 'Nf3' }));
    expect(screen.getByRole('button', { name: 'Nf3' })).toHaveAttribute('aria-current', 'true');

    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('button', { name: 'Nc6' })).toHaveAttribute('aria-current', 'true');

    await userEvent.keyboard('{ArrowLeft}');
    expect(screen.getByRole('button', { name: 'Nf3' })).toHaveAttribute('aria-current', 'true');
  });

  it('keeps stepping when the arrows are pressed repeatedly', async () => {
    // A single press cannot catch an effect bound to a stale index: the first
    // press works and every later one repeats it.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);
    await screen.findByRole('group', { name: /position/i });

    await userEvent.keyboard('{ArrowRight}{ArrowRight}{ArrowRight}');

    expect(screen.getByRole('button', { name: 'Nf3' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('button', { name: 'e4' })).not.toHaveAttribute('aria-current');
  });

  it('goes nowhere pressing back from the initial position', async () => {
    // useReplay refuses the out-of-range index, so the key handler needs no
    // bounds check of its own.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);
    await screen.findByRole('group', { name: /position/i });

    await userEvent.keyboard('{ArrowLeft}');

    expect(screen.getByLabelText('e2, white pawn')).toBeInTheDocument();
  });

  it('jumps to the final position and back to the start', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(A_GAME)));

    renderAt(ID);
    await screen.findByRole('group', { name: /position/i });

    await userEvent.keyboard('{End}');
    // A_GAME ends 3. Bb5, so the bishop stands on b5 and e2 is long vacated.
    expect(screen.getByLabelText('b5, white bishop')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Bb5' })).toHaveAttribute('aria-current', 'true');

    await userEvent.keyboard('{Home}');
    expect(screen.getByLabelText('e2, white pawn')).toBeInTheDocument();
  });

  it('says the game is not here, and offers no retry for it', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, 404)));

    renderAt(ID);

    expect(await screen.findByText(/no game with that identifier/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /games/i })).toBeInTheDocument();
  });

  it('refuses a malformed identifier without asking the server', async () => {
    const fetchStub = vi.fn();
    vi.stubGlobal('fetch', fetchStub);

    renderAt('not-a-uuid');

    expect(await screen.findByText(/identifier is invalid/i)).toBeInTheDocument();
    expect(fetchStub).not.toHaveBeenCalled();
    // Retrying an unparsable identifier cannot succeed, so it offers none.
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
    // A malformed URL is still a dead end with a way out.
    expect(screen.getByRole('link', { name: /games/i })).toBeInTheDocument();
  });

  it('offers a retry when the request failed, and recovers', async () => {
    const fetchStub = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse(A_GAME));
    vi.stubGlobal('fetch', fetchStub);

    renderAt(ID);

    expect(await screen.findByRole('alert')).toHaveTextContent(/failed to fetch/i);
    // A failed request is still a dead end with a way out, same as the other two.
    expect(screen.getByRole('link', { name: /games/i })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByText('Carlsen, M (2839)')).toBeInTheDocument();
  });

  it('shows what it can when the moves cannot be replayed', async () => {
    const unreplayable = { ...A_GAME, movetext: '1. e4 e5 2. Qxf7' };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(unreplayable)));

    renderAt(ID);

    // The header still renders, the stored movetext is visible, and the failure
    // is explained rather than the page going blank.
    expect(await screen.findByText('Carlsen, M (2839)')).toBeInTheDocument();
    expect(screen.getByText(/1\. e4 e5 2\. Qxf7/)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toBeInTheDocument();
    // The board still renders, at the one position it can honestly show.
    expect(screen.getByLabelText('e2, white pawn')).toBeInTheDocument();
  });
});
