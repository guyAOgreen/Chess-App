import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Chessboard } from './Chessboard';
import { squaresOf } from './squares';

const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
const EMPTY = '8/8/8/8/8/8/8/8 w - - 0 1';

describe('squaresOf', () => {
  it('expands a placement into 64 squares, a8 first', () => {
    const squares = squaresOf(START.split(' ')[0])!;

    expect(squares).toHaveLength(64);
    expect(squares[0]).toMatchObject({ name: 'a8', piece: 'r' });
    expect(squares[63]).toMatchObject({ name: 'h1', piece: 'R' });
  });

  it('expands digit runs into empty squares', () => {
    const squares = squaresOf(EMPTY.split(' ')[0])!;

    expect(squares).toHaveLength(64);
    expect(squares.every((square) => square.piece === null)).toBe(true);
  });

  it('alternates square colour, with a1 dark', () => {
    const squares = squaresOf(START.split(' ')[0])!;
    const a1 = squares.find((square) => square.name === 'a1')!;
    const h1 = squares.find((square) => square.name === 'h1')!;

    expect(a1.light).toBe(false);
    expect(h1.light).toBe(true);
  });

  it('refuses a placement that does not describe a board', () => {
    // A shifted or incomplete board must fail, not render quietly wrong.
    expect(squaresOf('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP')).toBeNull();
    expect(squaresOf('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNRR')).toBeNull();
    expect(squaresOf('')).toBeNull();
  });
});

describe('Chessboard', () => {
  it('places the pieces the FEN describes', () => {
    render(<Chessboard fen={START} />);

    expect(screen.getByLabelText('a1, white rook')).toBeInTheDocument();
    expect(screen.getByLabelText('e8, black king')).toBeInTheDocument();
  });

  it('names each occupied square by coordinate and piece', () => {
    // A screen reader hearing "white pawn" 8 times learns nothing about where.
    render(<Chessboard fen={START} />);

    expect(screen.getByLabelText('e2, white pawn')).toBeInTheDocument();
    expect(screen.getByLabelText('d7, black pawn')).toBeInTheDocument();
  });

  it('announces nothing for empty squares', () => {
    render(<Chessboard fen={EMPTY} />);

    expect(screen.queryAllByRole('img')).toHaveLength(0);
  });

  it('has an accessible name identifying it as the position', () => {
    render(<Chessboard fen={START} />);

    expect(screen.getByRole('group', { name: /position/i })).toBeInTheDocument();
  });

  it('says so when the position cannot be read', () => {
    render(<Chessboard fen="not-a-fen" />);

    expect(screen.getByText(/could not be read/i)).toBeInTheDocument();
  });

  it('points each square at its own piece image', () => {
    const { container } = render(<Chessboard fen={START} />);
    expect(container.querySelector('[aria-label="a1, white rook"] img')).toHaveAttribute(
      'src',
      '/pieces/wr.svg',
    );
    expect(container.querySelector('[aria-label="e8, black king"] img')).toHaveAttribute(
      'src',
      '/pieces/bk.svg',
    );
    expect(container.querySelectorAll('img')).toHaveLength(32);
  });
});
