/** The vertical extent of an element, as `getBoundingClientRect` reports it. */
export interface Band {
  top: number;
  bottom: number;
}

/**
 * How far to move a scroll container so that a move inside it is visible.
 *
 * Returns the amount to add to the container's own `scrollTop` — zero when the
 * move is already fully in view, and otherwise the smallest movement that brings
 * it in, so the list never jumps further than it must.
 *
 * This exists as a pure function of two bands rather than as a call to
 * `scrollIntoView`, which per the CSSOM View specification scrolls **every**
 * scrollable ancestor, the document included. Its `block` option chooses the
 * alignment within each container, not which container moves. On the stacked
 * layout the page itself scrolls, so `scrollIntoView` would push the board out
 * of view while bringing a late move in — the exact defect the move list's
 * height bound was added to prevent.
 *
 * It is also the only part of this that jsdom can test: jsdom performs no layout,
 * so every rect it reports is zero and nothing about the applied scroll is
 * observable there.
 */
export function scrollOffsetFor(container: Band, move: Band): number {
  if (move.top < container.top) {
    return move.top - container.top;
  }
  if (move.bottom > container.bottom) {
    return move.bottom - container.bottom;
  }
  return 0;
}
