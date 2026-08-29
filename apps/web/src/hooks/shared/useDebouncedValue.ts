import { useEffect, useState } from 'react';

/**
 * The value, held back until it has stopped changing for `delayMs`.
 *
 * <p>Shared rather than living with the games feature: nothing about waiting for
 * a value to settle is about games. The timer is cleared on every change and on
 * unmount, so only the last value in a burst is ever reported.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [settled, setSettled] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return settled;
}
