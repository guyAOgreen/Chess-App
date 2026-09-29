import { describe, expect, it } from 'vitest';
import { scrollOffsetFor } from './moveScroll';

/** A vertical band, as `getBoundingClientRect` reports one. */
const band = (top: number, bottom: number) => ({ top, bottom });

describe('scrollOffsetFor', () => {
  it('does not scroll a move that is already fully visible', () => {
    expect(scrollOffsetFor(band(100, 580), band(200, 224))).toBe(0);
  });

  it('does not scroll a move flush with the top or the bottom edge', () => {
    expect(scrollOffsetFor(band(100, 580), band(100, 124))).toBe(0);
    expect(scrollOffsetFor(band(100, 580), band(556, 580))).toBe(0);
  });

  it('scrolls up by exactly the amount a move sits above the window', () => {
    // 40px above the top edge, so the list scrolls back 40px and no further.
    expect(scrollOffsetFor(band(100, 580), band(60, 84))).toBe(-40);
  });

  it('scrolls down by exactly the amount a move sits below the window', () => {
    // Its bottom is 30px past the bottom edge.
    expect(scrollOffsetFor(band(100, 580), band(586, 610))).toBe(30);
  });

  it('scrolls a partly visible move fully into view, not past it', () => {
    expect(scrollOffsetFor(band(100, 580), band(570, 594))).toBe(14);
    expect(scrollOffsetFor(band(100, 580), band(90, 114))).toBe(-10);
  });

  it('aligns the top of a move taller than the window', () => {
    // Both edges are outside, so favour the start of the move rather than
    // scrolling past it to reach its bottom.
    expect(scrollOffsetFor(band(100, 200), band(80, 400))).toBe(-20);
  });
});
