import { BackendHealthCard } from '../features/system-health/components/BackendHealthCard';
import { useBackendHealth } from '../features/system-health/hooks/useBackendHealth';
import { GamesPage } from '../features/games/pages/GamesPage';

export default function App() {
  const health = useBackendHealth();

  return (
    <main>
      <h1>Chess Prep</h1>
      <GamesPage />
      <BackendHealthCard state={health} />
    </main>
  );
}
