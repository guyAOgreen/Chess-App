import { useMemo } from 'react';
import { Link, useParams } from 'react-router';
import styles from './GameViewerPage.module.css';
import { Chessboard } from '../components/Chessboard';
import { GameHeader } from '../components/GameHeader';
import { MoveList } from '../components/MoveList';
import { useGame } from '../hooks/useGame';
import { useReplay } from '../hooks/useReplay';
import { replay } from '../replay';
import type { Game } from '../types/game';

/**
 * One game, replayed.
 *
 * The page owns every state that is about the request rather than about a board:
 * an identifier that cannot be valid, a game that is not here, a request that
 * failed, and a game whose moves would not replay.
 */
export function GameViewerPage() {
  const { id } = useParams();
  const { state, retry } = useGame(id);

  if (state.kind === 'loading') {
    return <p role="status">Loading game…</p>;
  }

  if (state.kind === 'invalid-id') {
    return (
      <section className={styles.problem}>
        <p>That game identifier is invalid.</p>
        <Link to="/">Back to games</Link>
      </section>
    );
  }

  if (state.kind === 'not-found') {
    return (
      <section className={styles.problem}>
        <p>No game with that identifier.</p>
        <Link to="/">Back to games</Link>
      </section>
    );
  }

  if (state.kind === 'failed') {
    return (
      <section className={styles.problem} role="alert">
        <p>{state.message}</p>
        <button type="button" onClick={retry}>
          Retry
        </button>
      </section>
    );
  }

  // Keyed by game id so a stale ply index cannot survive a move to another game.
  return <GameViewer key={state.game.id} game={state.game} />;
}

/**
 * A loaded game. Separate so that the key above resets its selection, and so
 * that the replay runs once per game rather than once per render.
 */
function GameViewer({ game }: { game: Game }) {
  const replayed = useMemo(() => replay(game.movetext), [game.movetext]);
  const { current, select } = useReplay(replayed.plies);

  return (
    <article className={styles.viewer}>
      <GameHeader game={game} />

      {replayed.error !== null ? (
        <section className={styles.unreplayable}>
          <div role="alert">
            <p>These moves could not be replayed: {replayed.error}</p>
          </div>
          <pre className={styles.movetext}>{game.movetext}</pre>
          <Chessboard fen={replayed.plies[0].fen} />
        </section>
      ) : (
        <div className={styles.board}>
          <Chessboard fen={replayed.plies[current].fen} />
          <MoveList plies={replayed.plies} current={current} onSelect={select} />
        </div>
      )}

      <Link to="/">Back to games</Link>
    </article>
  );
}
