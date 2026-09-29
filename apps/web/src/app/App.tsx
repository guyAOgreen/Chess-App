import { BrowserRouter, Route, Routes } from 'react-router';
import { HomePage } from './HomePage';
import { GameViewerPage } from '../features/games/pages/GameViewerPage';

/**
 * The shell and the routes.
 *
 * Browser history, not hash routing — which is a deployment requirement as much
 * as a code one: any static host serving this must rewrite unknown application
 * paths such as `/games/{id}` to `index.html` while leaving `/api/*` to the
 * backend. Vite's dev server already does. Without that rule, in-app navigation
 * works but refreshing or sharing a viewer URL returns the host's 404.
 */
export default function App() {
  return (
    <BrowserRouter>
      <main>
        <h1>Chess Prep</h1>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/games/:id" element={<GameViewerPage />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}
