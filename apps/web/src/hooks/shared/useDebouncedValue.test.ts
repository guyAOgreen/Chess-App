import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDebouncedValue } from './useDebouncedValue';

describe('useDebouncedValue', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('reports the first value without waiting', () => {
    const { result } = renderHook(() => useDebouncedValue('Hastings', 300));

    expect(result.current).toBe('Hastings');
  });

  it('withholds a new value until the delay has passed', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'Hastings' },
    });

    rerender({ value: 'Hastings Premier' });
    expect(result.current).toBe('Hastings');

    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(result.current).toBe('Hastings Premier');
  });

  it('restarts the delay when the value changes again', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'H' },
    });

    rerender({ value: 'Ha' });
    act(() => {
      vi.advanceTimersByTime(200);
    });
    rerender({ value: 'Has' });
    act(() => {
      vi.advanceTimersByTime(200);
    });

    // 400ms have passed, but only 200ms since the last change.
    expect(result.current).toBe('H');

    act(() => {
      vi.advanceTimersByTime(100);
    });
    expect(result.current).toBe('Has');
  });

  it('leaves no pending timer after unmount', () => {
    // A plain "advancing timers after unmount does not throw" assertion would pass
    // even without clearTimeout, because React 19 silently no-ops a state update on
    // an unmounted component instead of warning or throwing. Asserting on the fake
    // timer queue itself catches a missing cleanup without coupling to which API
    // (clearTimeout vs. some future React internal) is used to achieve it.
    const { rerender, unmount } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'a' },
    });

    rerender({ value: 'b' });
    unmount();

    expect(vi.getTimerCount()).toBe(0);
  });

  it('handles an initial undefined value and settles back to undefined', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: undefined as string | undefined },
    });

    expect(result.current).toBeUndefined();

    rerender({ value: 'Hastings' });
    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(result.current).toBe('Hastings');

    rerender({ value: undefined });
    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(result.current).toBeUndefined();
  });

  it('uses the new delay when delayMs changes without the value changing', () => {
    // Changing value and delayMs in the same rerender would not catch a dependency
    // array missing delayMs: the effect reruns anyway because value changed, and it
    // captures whatever delayMs is current in that render's closure regardless of
    // the dependency array. To isolate the delayMs dependency, change delayMs on a
    // rerender where the value stays the same, so a stale effect (still running on
    // the old timer, scheduled with the old delay) becomes observable.
    const { result, rerender } = renderHook(({ value, delayMs }) => useDebouncedValue(value, delayMs), {
      initialProps: { value: 'a', delayMs: 1000 },
    });

    rerender({ value: 'b', delayMs: 1000 });
    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(result.current).toBe('a'); // still mid-way through the original 1000ms wait

    rerender({ value: 'b', delayMs: 100 });
    act(() => {
      vi.advanceTimersByTime(100);
    });
    expect(result.current).toBe('b');
  });
});
