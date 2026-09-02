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

  it('refuses a selection made after the plies list has shrunk, not just one made before', () => {
    // `select` must recompute its bound against the current plies on every
    // render, not close over the length that was current when it was first
    // created. If it did, this select (39, valid for the original 40-length
    // list) would be wrongly accepted after the rerender to 5, and the
    // reported index would land on the clamp bound (4) rather than staying
    // refused at the untouched initial value (0).
    const { result, rerender } = renderHook(({ p }) => useReplay(p), {
      initialProps: { p: plies(40) },
    });

    rerender({ p: plies(5) });
    act(() => result.current.select(39));

    expect(result.current.current).toBe(0);
  });

  it('reports an out-of-range index for an empty plies array, by contract rather than by accident', () => {
    // `replay` always prepends the initial position, so an empty array never
    // reaches this hook in practice. This pins the degenerate case explicitly
    // rather than leaving it to whatever `Math.min` happens to produce.
    const { result } = renderHook(() => useReplay([]));

    expect(result.current.current).toBe(-1);
  });
});
