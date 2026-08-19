import { useEffect, useState } from 'react';
import { fetchBackendHealth, type BackendHealth } from '../api/health';

export type HealthState =
  | { kind: 'loading' }
  | { kind: 'ready'; health: BackendHealth }
  | { kind: 'unreachable'; message: string };

export function useBackendHealth(): HealthState {
  const [state, setState] = useState<HealthState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;

    fetchBackendHealth()
      .then((health) => {
        if (active) {
          setState({ kind: 'ready', health });
        }
      })
      .catch((error: unknown) => {
        if (active) {
          const message = error instanceof Error ? error.message : String(error);
          setState({ kind: 'unreachable', message });
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}
