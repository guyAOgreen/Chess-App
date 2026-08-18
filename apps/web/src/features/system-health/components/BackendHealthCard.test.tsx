import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BackendHealthCard } from './BackendHealthCard';

describe('BackendHealthCard', () => {
  it('reports that the backend is being contacted', () => {
    render(<BackendHealthCard state={{ kind: 'loading' }} />);
    expect(screen.getByText(/contacting the backend/i)).toBeInTheDocument();
  });

  it('reports the backend status and each component', () => {
    render(
      <BackendHealthCard
        state={{
          kind: 'ready',
          health: { status: 'UP', components: { db: { status: 'UP' } } },
        }}
      />,
    );
    expect(screen.getByText(/backend: UP/i)).toBeInTheDocument();
    expect(screen.getByText(/db: UP/i)).toBeInTheDocument();
  });

  it('reports why the backend could not be reached', () => {
    render(<BackendHealthCard state={{ kind: 'unreachable', message: 'Failed to fetch' }} />);
    expect(screen.getByText(/backend unreachable/i)).toBeInTheDocument();
    expect(screen.getByText(/failed to fetch/i)).toBeInTheDocument();
  });
});
