import { fireEvent, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useMoveKeys } from './useMoveKeys';

/**
 * `fireEvent` returns false when the event was cancelled, which is how these
 * tests observe `preventDefault` without reaching into the event object.
 */
function press(key: string, init: KeyboardEventInit = {}, target: Element | Document = document) {
  return fireEvent.keyDown(target, { key, ...init });
}

afterEach(() => {
  document.body.innerHTML = '';
});

describe('useMoveKeys', () => {
  it('steps forward a ply', () => {
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 3, count: 10, select }));

    press('ArrowRight');

    expect(select).toHaveBeenCalledExactlyOnceWith(4);
  });

  it('steps back a ply', () => {
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 3, count: 10, select }));

    press('ArrowLeft');

    expect(select).toHaveBeenCalledExactlyOnceWith(2);
  });

  it('jumps to the initial position', () => {
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 7, count: 10, select }));

    press('Home');

    expect(select).toHaveBeenCalledExactlyOnceWith(0);
  });

  it('jumps to the final position', () => {
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 2, count: 10, select }));

    press('End');

    expect(select).toHaveBeenCalledExactlyOnceWith(9);
  });

  it('delegates the bounds to select rather than checking them itself', () => {
    // `useReplay.select` already refuses an out-of-range index, so duplicating
    // that rule here would be a second place for it to drift.
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 0, count: 10, select }));

    press('ArrowLeft');

    expect(select).toHaveBeenCalledExactlyOnceWith(-1);
  });

  it('ignores a key it does not handle, and leaves it uncancelled', () => {
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 3, count: 10, select }));

    const notCancelled = press('a');

    expect(select).not.toHaveBeenCalled();
    expect(notCancelled).toBe(true);
  });

  it('cancels the keys it handles, so the page does not also scroll', () => {
    renderHook(() => useMoveKeys({ current: 3, count: 10, select: vi.fn() }));

    expect(press('ArrowRight')).toBe(false);
    expect(press('ArrowLeft')).toBe(false);
    expect(press('Home')).toBe(false);
    expect(press('End')).toBe(false);
  });

  it('leaves arrows alone inside a text field', () => {
    // #17 puts correction inputs beside this viewer; stealing their arrows
    // would make them unusable.
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 3, count: 10, select }));
    const input = document.createElement('input');
    document.body.append(input);

    const notCancelled = press('ArrowRight', {}, input);

    expect(select).not.toHaveBeenCalled();
    expect(notCancelled).toBe(true);
  });

  it('leaves arrows alone inside a textarea and a select', () => {
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 3, count: 10, select }));
    const textarea = document.createElement('textarea');
    const dropdown = document.createElement('select');
    document.body.append(textarea, dropdown);

    press('ArrowRight', {}, textarea);
    press('ArrowRight', {}, dropdown);

    expect(select).not.toHaveBeenCalled();
  });

  it('leaves arrows alone inside an editable region, and inside a child of one', () => {
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 3, count: 10, select }));
    const editable = document.createElement('div');
    editable.setAttribute('contenteditable', 'true');
    const child = document.createElement('span');
    editable.append(child);
    document.body.append(editable);

    press('ArrowRight', {}, editable);
    press('ArrowRight', {}, child);

    expect(select).not.toHaveBeenCalled();
  });

  it('handles arrows inside a region explicitly marked not editable', () => {
    // contenteditable="false" nested in an editable region is not a text field,
    // so it does not own the arrows.
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 3, count: 10, select }));
    const frozen = document.createElement('div');
    frozen.setAttribute('contenteditable', 'false');
    document.body.append(frozen);

    press('ArrowRight', {}, frozen);

    expect(select).toHaveBeenCalledExactlyOnceWith(4);
  });

  it('leaves modified keys to the browser', () => {
    const select = vi.fn();
    renderHook(() => useMoveKeys({ current: 3, count: 10, select }));

    press('ArrowRight', { ctrlKey: true });
    press('ArrowRight', { metaKey: true });
    press('ArrowRight', { altKey: true });

    expect(select).not.toHaveBeenCalled();
  });

  it('stops listening once unmounted', () => {
    const select = vi.fn();
    const { unmount } = renderHook(() => useMoveKeys({ current: 3, count: 10, select }));

    unmount();
    press('ArrowRight');

    expect(select).not.toHaveBeenCalled();
  });
});
