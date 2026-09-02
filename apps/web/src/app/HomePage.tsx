import { BackendHealthCard } from '../features/system-health/components/BackendHealthCard';
import { useBackendHealth } from '../features/system-health/hooks/useBackendHealth';
import { GamesPage } from '../features/games/pages/GamesPage';

/**
 * The list, with the backend's health beneath it.
 *
 * Health lives here rather than in `App` so that opening a game does not also
 * poll Actuator.
 */
export function HomePage() {
  const health = useBackendHealth();

  return (
    <>
      <GamesPage />
      <BackendHealthCard state={health} />
    </>
  );
}
