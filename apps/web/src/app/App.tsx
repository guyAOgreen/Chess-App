import { BackendHealthCard } from '../features/system-health/components/BackendHealthCard';
import { useBackendHealth } from '../features/system-health/hooks/useBackendHealth';

export default function App() {
  const health = useBackendHealth();

  return (
    <main>
      <h1>Chess Prep</h1>
      <BackendHealthCard state={health} />
    </main>
  );
}
