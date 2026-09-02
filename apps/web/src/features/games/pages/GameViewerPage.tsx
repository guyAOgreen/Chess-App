// apps/web/src/features/games/pages/GameViewerPage.tsx — replaced in full by Task 9
import { useParams } from 'react-router';
import { useGame } from '../hooks/useGame';

export function GameViewerPage() {
  const { id } = useParams();
  const { state } = useGame(id);
  return <div role={state.kind === 'failed' ? 'alert' : undefined}>{state.kind}</div>;
}
