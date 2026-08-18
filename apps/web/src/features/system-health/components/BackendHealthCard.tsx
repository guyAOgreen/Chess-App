import type { HealthState } from '../hooks/useBackendHealth';

export function BackendHealthCard({ state }: { state: HealthState }) {
  if (state.kind === 'loading') {
    return <p>Contacting the backend…</p>;
  }

  if (state.kind === 'unreachable') {
    return (
      <section>
        <p>Backend unreachable</p>
        <p>{state.message}</p>
      </section>
    );
  }

  const components = Object.entries(state.health.components ?? {});

  return (
    <section>
      <p>Backend: {state.health.status}</p>
      <ul>
        {components.map(([name, component]) => (
          <li key={name}>
            {name}: {component.status}
          </li>
        ))}
      </ul>
    </section>
  );
}
