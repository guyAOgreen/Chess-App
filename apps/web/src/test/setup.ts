import '@testing-library/jest-dom/vitest';

/**
 * jsdom performs no layout, so it implements no scrolling and
 * `Element.prototype.scrollIntoView` does not exist at all.
 *
 * `MoveList` calls it to keep the selected move visible inside its own scroll
 * container, so without this shim every test that renders a move list would
 * throw. Tests that care about the behaviour spy on it per element — asserting
 * the call is the ceiling here, because no scroll position ever changes in
 * jsdom for an assertion to observe.
 */
if (typeof Element.prototype.scrollIntoView !== 'function') {
  Element.prototype.scrollIntoView = function scrollIntoView() {
    // Deliberately empty: there is nothing to scroll.
  };
}
