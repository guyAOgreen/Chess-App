import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useReplay } from './useReplay';
import type { Ply } from '../types/ply';

function plies(count: number): Ply[] {
  return Array.from({ length: count }, (_, index) => ({
    index,
    moveNumber: Math.ceil(index / 2),
    colour: index === 0 ? null : index % 2 === 1 ? ('white' as const) : ('black' as const),
    san: index === 0 ? null : `move${index}`,
    fen: `fen${index}`,
  }));
}

describe('useReplay', () => {
  it('starts at the initial position', () => {
    const { result } = renderHook(() => useReplay(plies(5)));

    expect(result.current.current).toBe(0);
  });

  it('selects a ply', () => {
    const { result } = renderHook(() => useReplay(plies(5)));

    act(() => result.current.select(3));

    expect(result.current.current).toBe(3);
  });

  it('refuses an index past the end rather than producing an undefined position', () => {
    const { result } = renderHook(() => useReplay(plies(5)));

    act(() => result.current.select(3));
    act(() => result.current.select(99));

    expect(result.current.current).toBe(3);
  });

  it('refuses a negative index', () => {
    const { result } = renderHook(() => useReplay(plies(5)));

    act(() => result.current.select(2));
    act(() => result.current.select(-1));

    expect(result.current.current).toBe(2);
  });

  it('never reports an index past a shorter set of plies', () => {
    // Navigating from a long game to a short one must not index past the end.
    const { result, rerender } = renderHook(({ p }) => useReplay(p), {
      initialProps: { p: plies(40) },
    });

    act(() => result.current.select(39));
    rerender({ p: plies(5) });

    expect(result.current.current).toBeLessThan(5);
  });
});
