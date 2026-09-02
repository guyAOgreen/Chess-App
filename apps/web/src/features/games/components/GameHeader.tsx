import styles from './GameHeader.module.css';
import { orDash, resultLabel, sideLabel, sourceLabel, spokenResultLabel } from '../format';
import type { Game } from '../types/game';

/**
 * Who played, where, and how it finished.
 *
 * Uses the same formatting helpers as the list, so a game reads identically in
 * both places.
 */
export function GameHeader({ game }: { game: Game }) {
  // JSX strips the whitespace between the three spans, so without an explicit
  // accessible name the heading's textContent runs the three together with no
  // separation (e.g. "Green, G*Opp, O") — the `gap` in the CSS module is
  // visual only and a screen reader never sees it. The spans stay for layout;
  // this label is what assistive tech actually announces.
  //
  // Uses spokenResultLabel, not resultLabel: the visual tokens (`1-0`, `½-½`,
  // `*`) are exactly right on screen but do not speak — a screen reader either
  // skips a bare `*` or says "star", and reads `½-½` as "one half one half".
  // The spans below keep resultLabel; only this label needs the spoken form.
  const heading = `${sideLabel(game.white)} versus ${sideLabel(game.black)}, ${spokenResultLabel(game.result)}`;

  return (
    <header className={styles.header}>
      <h2 className={styles.players} aria-label={heading}>
        <span>{sideLabel(game.white)}</span>
        <span className={styles.result}>{resultLabel(game.result)}</span>
        <span>{sideLabel(game.black)}</span>
      </h2>
      <dl className={styles.meta}>
        <div>
          <dt>Event</dt>
          <dd>{orDash(game.event)}</dd>
        </div>
        <div>
          <dt>Site</dt>
          <dd>{orDash(game.site)}</dd>
        </div>
        <div>
          <dt>Round</dt>
          <dd>{orDash(game.round)}</dd>
        </div>
        <div>
          <dt>Date</dt>
          <dd>{orDash(game.playedOn)}</dd>
        </div>
        <div>
          <dt>ECO</dt>
          <dd>{orDash(game.eco)}</dd>
        </div>
        <div>
          <dt>Source</dt>
          <dd>{sourceLabel(game.source)}</dd>
        </div>
      </dl>
    </header>
  );
}
