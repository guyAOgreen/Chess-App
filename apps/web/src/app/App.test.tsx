import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';

function jsonResponse(body: unknown): Response {
  return { ok: true, status: 200, json: async () => body } as unknown as Response;
}

const EMPTY_PAGE = { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 };

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.pushState({}, '', '/');
});

describe('App', () => {
  it('renders the application name', () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    render(<App />);

    expect(screen.getByRole('heading', { name: /chess prep/i })).toBeInTheDocument();
  });

  it('shows the games list at the root', () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_PAGE)));

    render(<App />);

    expect(screen.getByRole('heading', { name: /games/i })).toBeInTheDocument();
  });

  it('shows the viewer at /games/:id', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    window.history.pushState({}, '', '/games/11111111-1111-1111-1111-111111111111');

    render(<App />);

    // The viewer is what fetches a single game; the list never does.
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /^games$/i })).not.toBeInTheDocument();
  });
});
