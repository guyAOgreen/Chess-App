import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';

describe('Button', () => {
  it('does not submit a surrounding form unless asked to', () => {
    // A bare <button> is type="submit". Every button so far is an action rather
    // than a submission, so the safe default is the one that cannot submit.
    render(<Button>Act</Button>);

    expect(screen.getByRole('button', { name: 'Act' })).toHaveAttribute('type', 'button');
  });

  it('can still be a submit button', () => {
    render(<Button type="submit">Save</Button>);

    expect(screen.getByRole('button', { name: 'Save' })).toHaveAttribute('type', 'submit');
  });

  it('passes through what a button takes', async () => {
    const onClick = vi.fn();
    render(
      <Button onClick={onClick} aria-describedby="hint">
        Retry
      </Button>,
    );

    const button = screen.getByRole('button', { name: 'Retry' });
    await userEvent.click(button);

    expect(onClick).toHaveBeenCalledTimes(1);
    expect(button).toHaveAttribute('aria-describedby', 'hint');
  });

  it('keeps its own styling when the caller adds a layout class', () => {
    render(<Button className="placed">Next</Button>);

    const button = screen.getByRole('button', { name: 'Next' });
    expect(button).toHaveClass('placed');
    expect(button.classList.length).toBe(2);
  });

  it('does nothing when disabled', async () => {
    const onClick = vi.fn();
    render(
      <Button disabled onClick={onClick}>
        Previous
      </Button>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Previous' }));

    expect(onClick).not.toHaveBeenCalled();
  });
});
