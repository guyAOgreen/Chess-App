import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { GameFilters } from './GameFilters';

describe('GameFilters', () => {
  it('raises the chosen result', async () => {
    const onChange = vi.fn();
    render(<GameFilters values={{}} onChange={onChange} onClear={vi.fn()} />);

    await userEvent.selectOptions(screen.getByLabelText(/result/i), 'DRAW');

    expect(onChange).toHaveBeenCalledWith('result', 'DRAW');
  });

  it('raises undefined, not an empty string, when the result is set back to any', async () => {
    const onChange = vi.fn();
    render(<GameFilters values={{ result: 'DRAW' }} onChange={onChange} onClear={vi.fn()} />);

    await userEvent.selectOptions(screen.getByLabelText(/result/i), '');

    expect(onChange).toHaveBeenCalledExactlyOnceWith('result', undefined);
  });

  it('raises the event term as it is typed', async () => {
    const onChange = vi.fn();
    render(<GameFilters values={{}} onChange={onChange} onClear={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/event/i), 'H');

    expect(onChange).toHaveBeenCalledWith('event', 'H');
  });

  it('raises undefined when the event term is emptied', async () => {
    const onChange = vi.fn();
    render(<GameFilters values={{ event: 'H' }} onChange={onChange} onClear={vi.fn()} />);

    await userEvent.clear(screen.getByLabelText(/event/i));

    expect(onChange).toHaveBeenCalledExactlyOnceWith('event', undefined);
  });

  it('limits the event term to what the API accepts', () => {
    // GameListParams declares @Size(max = 255); a longer term is a 400 the form
    // can simply not produce.
    render(<GameFilters values={{}} onChange={vi.fn()} onClear={vi.fn()} />);

    expect(screen.getByLabelText(/event/i)).toHaveAttribute('maxlength', '255');
  });

  it('stops the date range being ordered backwards', () => {
    // from <= to, so from is the earliest the to input allows, and to is the
    // latest the from input allows. Each input's *own* bound must stay unset —
    // "from" must not cap itself with its own value, only with "to"'s.
    render(
      <GameFilters
        values={{ from: '2024-01-01', to: '2024-12-31' }}
        onChange={vi.fn()}
        onClear={vi.fn()}
      />,
    );

    const from = screen.getByLabelText(/from/i);
    const to = screen.getByLabelText(/to/i);
    expect(from).toHaveAttribute('max', '2024-12-31');
    expect(from).not.toHaveAttribute('min');
    expect(to).toHaveAttribute('min', '2024-01-01');
    expect(to).not.toHaveAttribute('max');
  });

  it('raises a clear', async () => {
    const onClear = vi.fn();
    render(<GameFilters values={{ result: 'DRAW' }} onChange={vi.fn()} onClear={onClear} />);

    await userEvent.click(screen.getByRole('button', { name: /clear/i }));

    expect(onClear).toHaveBeenCalled();
  });

  it('raises undefined when the event term is only whitespace', async () => {
    // The backend treats a blank searchTerm as absent and documents that a blank
    // term reaching the query would silently drop every game with no event — so
    // "   " must not be raised as a real filter value.
    const onChange = vi.fn();
    render(<GameFilters values={{}} onChange={onChange} onClear={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/event/i), '   ');

    expect(onChange).toHaveBeenLastCalledWith('event', undefined);
  });

  it('never submits: Enter in a field does not reload the page', () => {
    // A real submit is a full-page reload jsdom cannot perform; if the guard is
    // missing, the browser's default action fires and this handler never does,
    // so asserting the event arrived with defaultPrevented is the reliable proxy.
    render(<GameFilters values={{}} onChange={vi.fn()} onClear={vi.fn()} />);

    const form = screen.getByLabelText(/event/i).closest('form') as HTMLFormElement;
    const submitEvent = new Event('submit', { bubbles: true, cancelable: true });
    form.dispatchEvent(submitEvent);

    expect(submitEvent.defaultPrevented).toBe(true);
  });

  it('makes the clear button inert to submission', () => {
    render(<GameFilters values={{}} onChange={vi.fn()} onClear={vi.fn()} />);

    expect(screen.getByRole('button', { name: /clear/i })).toHaveAttribute('type', 'button');
  });
});
