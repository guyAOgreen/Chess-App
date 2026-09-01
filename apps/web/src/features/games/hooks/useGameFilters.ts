import { useCallback, useMemo, useReducer } from 'react';
import { useDebouncedValue } from '../../../hooks/shared/useDebouncedValue';
import type { GameFilterValues, GamesQuery } from '../types/game';

const EVENT_DEBOUNCE_MS = 300;

interface FilterState {
  values: GameFilterValues;
  page: number;
}

type FilterAction =
  | { type: 'filter'; patch: Partial<GameFilterValues> }
  | { type: 'page'; page: number }
  | { type: 'clear' };

const INITIAL: FilterState = { values: {}, page: 0 };

/**
 * Every filter action returns to page 0 in the same dispatch. Doing it here
 * rather than in an effect that watches the filters means there is no render in
 * which the filters have changed and the page has not.
 */
function reduce(state: FilterState, action: FilterAction): FilterState {
  switch (action.type) {
    case 'filter':
      return { values: { ...state.values, ...action.patch }, page: 0 };
    case 'page':
      return { ...state, page: action.page };
    case 'clear':
      return INITIAL;
  }
}

export interface UseGameFilters {
  /** Raw, for the controlled inputs: updates on every keystroke. */
  values: GameFilterValues;
  /** What to request: the same filters with the event term debounced. */
  query: GamesQuery;
  isFiltered: boolean;
  setFilter: <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => void;
  setPage: (page: number) => void;
  clear: () => void;
}

/**
 * Filter and page state in one place, so a component never has to know that the
 * event term is debounced or that changing a filter moves the page.
 */
export function useGameFilters(): UseGameFilters {
  const [state, dispatch] = useReducer(reduce, INITIAL);
  const settledEvent = useDebouncedValue(state.values.event, EVENT_DEBOUNCE_MS);

  const setFilter = useCallback(
    <K extends keyof GameFilterValues>(key: K, value: GameFilterValues[K]) => {
      dispatch({ type: 'filter', patch: { [key]: value } });
    },
    [],
  );

  const setPage = useCallback((page: number) => dispatch({ type: 'page', page }), []);
  const clear = useCallback(() => dispatch({ type: 'clear' }), []);

  // Clearing after the event term has already settled leaves `query.event`
  // holding the old, pre-clear term for up to another 300ms while `values` and
  // `isFiltered` update immediately: `values.event` is undefined, but a request
  // for the old term is still in flight. Clearing mid-burst, before anything has
  // settled, is clean — there is no settled value yet to hold onto. This is
  // accepted as designed — it self-corrects once the debounce settles — not an
  // oversight.
  const query = useMemo<GamesQuery>(
    () => ({ ...state.values, event: settledEvent, page: state.page }),
    [state.values, state.page, settledEvent],
  );

  // GameFilters.orUndefined turns a blank control into `undefined` before it
  // ever reaches setFilter, so an empty string never enters `state.values`.
  const isFiltered = Object.values(state.values).some((value) => value !== undefined);

  return { values: state.values, query, isFiltered, setFilter, setPage, clear };
}
