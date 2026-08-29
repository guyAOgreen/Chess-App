import styles from './GamesPage.module.css';
import { GameFilters } from '../components/GameFilters';
import { GamePager } from '../components/GamePager';
import { GameTable } from '../components/GameTable';
import { useGameFilters } from '../hooks/useGameFilters';
import { useGames } from '../hooks/useGames';

/**
 * Composes the two hooks and the three components, and owns the states that are
 * about the page rather than about a table: nothing loaded yet, nothing matched,
 * nothing arrived.
 *
 * The filter form is rendered in every state. A user who filters into an empty
 * result has to be able to filter back out, and a user whose request failed has
 * to be able to change it.
 */
export function GamesPage() {
  const { values, query, isFiltered, setFilter, setPage, clear } = useGameFilters();
  const { state, retry } = useGames(query);

  return (
    <section className={styles.page}>
      <h2>Games</h2>

      <GameFilters values={values} onChange={setFilter} onClear={clear} />

      {state.kind === 'loading' && <p role="status">Loading games…</p>}

      {state.kind === 'failed' && (
        <div role="alert" className={styles.failure}>
          <p>{state.message}</p>
          <button type="button" onClick={retry}>
            Retry
          </button>
        </div>
      )}

      {state.kind === 'ready' &&
        (state.page.content.length === 0 ? (
          <p role="status">
            {isFiltered
              ? 'No games match these filters.'
              : 'No games yet. Import a PGN to add one.'}
          </p>
        ) : (
          <>
            <div className={styles.tableWrapper} aria-busy={state.refreshing}>
              <GameTable games={state.page.content} />
            </div>
            <GamePager
              page={state.page.page}
              totalElements={state.page.totalElements}
              totalPages={state.page.totalPages}
              onPageChange={setPage}
            />
          </>
        ))}
    </section>
  );
}
