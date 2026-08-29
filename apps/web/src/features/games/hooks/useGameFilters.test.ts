import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useGameFilters } from './useGameFilters';

describe('useGameFilters', () => {
  it('starts unfiltered on the first page', () => {
    const { result } = renderHook(() => useGameFilters());

    // toStrictEqual: toEqual ignores undefined-valued keys, so it cannot tell
    // an object with no keys apart from one with every key set to undefined.
    expect(result.current.values).toStrictEqual({});
    expect(result.current.query.page).toBe(0);
    expect(result.current.isFiltered).toBe(false);
  });

  it('keeps the page the user asked for', () => {
    const { result } = renderHook(() => useGameFilters());

    act(() => result.current.setPage(3));

    expect(result.current.query.page).toBe(3);
    expect(result.current.values).toStrictEqual({});
  });

  it('returns to the first page whenever a filter changes', () => {
    // The rule this hook exists for: page 5 of a result set that no longer has
    // five pages is an empty screen with no explanation.
    const { result } = renderHook(() => useGameFilters());

    act(() => result.current.setPage(5));
    act(() => result.current.setFilter('result', 'DRAW'));

    expect(result.current.query.page).toBe(0);
    expect(result.current.values.result).toBe('DRAW');
  });

  it('clears every filter and returns to the first page', () => {
    const { result } = renderHook(() => useGameFilters());

    act(() => result.current.setFilter('result', 'DRAW'));
    act(() => result.current.setFilter('from', '2024-01-01'));
    act(() => result.current.setPage(2));
    act(() => result.current.clear());

    // toStrictEqual, not toEqual: `clear` must produce a genuinely empty
    // object, not one where 'result' and 'from' are merely set to undefined.
    expect(result.current.values).toStrictEqual({});
    expect(result.current.query.page).toBe(0);
    expect(result.current.isFiltered).toBe(false);
  });

  it('is unfiltered again when the only filter is emptied', () => {
    const { result } = renderHook(() => useGameFilters());

    act(() => result.current.setFilter('result', 'DRAW'));
    expect(result.current.isFiltered).toBe(true);

    act(() => result.current.setFilter('result', undefined));
    expect(result.current.isFiltered).toBe(false);
  });

  describe('the event term', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('appears in values at once and in the query only once typing settles', () => {
      const { result } = renderHook(() => useGameFilters());

      act(() => result.current.setFilter('event', 'Hast'));

      expect(result.current.values.event).toBe('Hast');
      expect(result.current.query.event).toBeUndefined();

      act(() => {
        vi.advanceTimersByTime(300);
      });

      expect(result.current.query.event).toBe('Hast');
      // The page was already reset by the keystroke, so the request that finally
      // goes out asks for page 0.
      expect(result.current.query.page).toBe(0);
    });
  });
});
