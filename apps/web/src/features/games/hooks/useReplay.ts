import { useCallback, useState } from 'react';
import type { Ply } from '../types/ply';

export interface UseReplay {
  current: number;
  select: (index: number) => void;
}

/**
 * Which ply is being shown.
 *
 * Takes plies rather than movetext, so it never touches chess.js: replaying is
 * `replay`'s job, and this only holds a cursor into the result.
 *
 * Out-of-range selections are refused rather than clamped silently at the call
 * site, and the reported index is bounded by the current plies. The page also
 * keys the viewer by game id, so this is the second line of defence against a
 * stale index surviving a move to a shorter game — not the first.
 */
export function useReplay(plies: Ply[]): UseReplay {
  const [current, setCurrent] = useState(0);

  const select = useCallback(
    (index: number) => {
      setCurrent((previous) => (index >= 0 && index < plies.length ? index : previous));
    },
    [plies.length],
  );

  return { current: Math.min(current, Math.max(plies.length - 1, 0)), select };
}
