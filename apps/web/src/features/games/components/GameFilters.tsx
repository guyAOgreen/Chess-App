import styles from './GameFilters.module.css';
import { resultLabel } from '../format';
import { GAME_RESULTS, type GameFilterValues, type GameResult } from '../types/game';

/** The API treats blank and absent as the same thing, and so does the state: an
 * emptied control raises `undefined` rather than `''`. */
function orUndefined(value: string): string | undefined {
  return value.trim() === '' ? undefined : value;
}

export interface GameFiltersProps {
  values: GameFilterValues;
  onChange: <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => void;
  onClear: () => void;
}

/**
 * The filter controls. Every one of them can be operated: the endpoint's
 * `playerId` filter takes a UUID that nothing yet turns a name into, so it waits
 * for [#21](https://github.com/guyAOgreen/Chess-App/issues/21) rather than shipping
 * as a control nobody can use.
 *
 * Submission is prevented because there is nothing to submit — every change
 * applies as it is made.
 */
export function GameFilters({ values, onChange, onClear }: GameFiltersProps) {
  return (
    <form className={styles.filters} onSubmit={(event) => event.preventDefault()}>
      <label className={styles.field}>
        Result
        <select
          value={values.result ?? ''}
          onChange={(event) =>
            onChange('result', orUndefined(event.target.value) as GameResult | undefined)
          }
        >
          <option value="">Any</option>
          {GAME_RESULTS.map((result) => (
            <option key={result} value={result}>
              {resultLabel(result)}
            </option>
          ))}
        </select>
      </label>

      <label className={styles.field}>
        From
        <input
          type="date"
          value={values.from ?? ''}
          max={values.to}
          onChange={(event) => onChange('from', orUndefined(event.target.value))}
        />
      </label>

      <label className={styles.field}>
        To
        <input
          type="date"
          value={values.to ?? ''}
          min={values.from}
          onChange={(event) => onChange('to', orUndefined(event.target.value))}
        />
      </label>

      <label className={styles.field}>
        Event
        <input
          type="search"
          maxLength={255}
          placeholder="Any event"
          value={values.event ?? ''}
          onChange={(event) => onChange('event', orUndefined(event.target.value))}
        />
      </label>

      <button type="button" className={styles.clear} onClick={onClear}>
        Clear filters
      </button>
    </form>
  );
}
