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

  it('clears the pending timer on unmount', () => {
    // A plain "advancing timers after unmount does not throw" assertion would pass
    // even without clearTimeout, because React 19 silently no-ops a state update on
    // an unmounted component instead of warning or throwing. To actually catch a
    // missing `clearTimeout`, spy on the global and assert it fires on unmount.
    const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout');

    const { rerender, unmount } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'a' },
    });

    rerender({ value: 'b' });
    // The rerender itself triggers one clearTimeout call (cleanup of the effect
    // scheduled for 'a'). Reset the spy so the assertion below is only about unmount.
    clearTimeoutSpy.mockClear();

    unmount();

    expect(clearTimeoutSpy).toHaveBeenCalledTimes(1);

    // The timer for 'b' must not fire after unmount.
    act(() => {
      vi.advanceTimersByTime(300);
    });
  });
});
