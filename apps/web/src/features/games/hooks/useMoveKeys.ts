import { useEffect } from 'react';

export interface UseMoveKeysOptions {
  /** The ply currently shown. */
  current: number;
  /** How many plies there are, including the initial position. */
  count: number;
  /** `useReplay`'s selector. It refuses an out-of-range index, which is why
   * this hook does no bounds checking of its own. */
  select: (index: number) => void;
}

const FORM_CONTROLS = new Set(['INPUT', 'TEXTAREA', 'SELECT']);

/** An editable host, or anything inside one. `contenteditable="false"` nested
 * within an editable region is excluded, which is what it means. */
const EDITABLE = '[contenteditable]:not([contenteditable="false"])';

/**
 * Whether the key was pressed inside something that owns its own arrows.
 *
 * A text field uses the arrows to move a caret and Home/End to jump within a
 * line. Taking those would make the field unusable, which matters for
 * [#17](https://github.com/guyAOgreen/Chess-App/issues/17): its correction
 * screen puts inputs beside this viewer.
 *
 * Editability is tested through `closest` on the attribute rather than through
 * the `isContentEditable` property. Both are correct in a browser and both
 * handle nesting, but jsdom does not implement the property — so using it would
 * leave this guard passing in tests for a reason unrelated to whether it works.
 */
function ownsItsArrows(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return FORM_CONTROLS.has(target.tagName) || target.closest(EDITABLE) !== null;
}

/**
 * Arrow-key navigation through a replayed game.
 *
 * Listens on the document rather than on a focused element, so the keys work
 * the moment the page loads. That is what a viewer is expected to do, and
 * requiring a click first makes the feature invisible — a user presses an
 * arrow, nothing happens, and they conclude it is not there.
 *
 * The cost of listening globally is that the handler must decline what is not
 * its business: keys inside a form control, and keys held with a modifier,
 * which belong to the browser and the operating system.
 *
 * `preventDefault` is called only for keys actually handled. Cancelling
 * unconditionally would stop the page scrolling horizontally on every arrow
 * press and break Home/End everywhere else on the page.
 */
export function useMoveKeys({ current, count, select }: UseMoveKeysOptions): void {
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.ctrlKey || event.metaKey || event.altKey || ownsItsArrows(event.target)) {
        return;
      }

      switch (event.key) {
        case 'ArrowLeft':
          select(current - 1);
          break;
        case 'ArrowRight':
          select(current + 1);
          break;
        case 'Home':
          select(0);
          break;
        case 'End':
          select(count - 1);
          break;
        default:
          return;
      }

      event.preventDefault();
    }

    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [current, count, select]);
}
