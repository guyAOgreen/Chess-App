import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('App', () => {
  it('renders the application name', () => {
    // The app fetches health and games on mount; neither is what this asserts.
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    render(<App />);

    expect(screen.getByRole('heading', { name: /chess prep/i })).toBeInTheDocument();
  });
});
