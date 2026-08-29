import styles from './GamePager.module.css';

export interface GamePagerProps {
  /** Zero-based, as `GamePageResponse` reports it. */
  page: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

/**
 * Previous and Next, and where the user is.
 *
 * The envelope carries `totalPages` so a numbered pager is possible, and
 * nothing asks for one: a personal database is a handful of pages, and the data to
 * build one is already here the day it is wanted.
 */
export function GamePager({ page, totalElements, totalPages, onPageChange }: GamePagerProps) {
  const games = `${totalElements} ${totalElements === 1 ? 'game' : 'games'}`;

  return (
    <nav className={styles.pager} aria-label="Pagination">
      <button
        type="button"
        className={styles.step}
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        Previous
      </button>
      <span className={styles.position}>
        Page {page + 1} of {totalPages} · {games}
      </span>
      <button
        type="button"
        className={styles.step}
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </button>
    </nav>
  );
}
