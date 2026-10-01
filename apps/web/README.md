# web

The React + TypeScript + Vite frontend. How to run it alongside the backend is
in the [root README](../../README.md); this file covers how the code is
organised.

## Scripts

    yarn dev      # dev server, proxying /api and /actuator to the backend on 8080
    yarn test     # Vitest + Testing Library, once (yarn test:watch to watch)
    yarn lint     # Oxlint, configured in .oxlintrc.json
    yarn format   # Prettier, configured in .prettierrc.json (format:check to check only)
    yarn build    # type-check, then production build into dist/

## Layout

Code is organised by feature, not by technical layer:

    src/
    ├── app/              app shell, routing, home page
    ├── features/
    │   └── <feature>/
    │       ├── api/          requests to the backend for this feature
    │       ├── types/        the feature's types, including API response shapes
    │       ├── hooks/        request state and behaviour
    │       ├── components/   UI, each with its CSS Module beside it
    │       └── pages/        route-level components that compose the above
    ├── components/shared/  components used by more than one feature
    ├── hooks/shared/       hooks used by more than one feature
    ├── lib/              framework-free helpers (e.g. lib/api.ts)
    └── index.css         global styles and design tokens

Something belongs in a shared directory only once a second feature needs it.
A feature uses only the subdirectories it needs.

## Conventions

- **Data fetching** is a hand-rolled hook per request built on `getJson` from
  `lib/api.ts`, not a query library. Every hook returns a discriminated union
  of states. `useGames` and `useGame` are the model for a new hook: they also
  abort their request when its inputs change or the component unmounts, and
  expose `retry`. `useBackendHealth` is simpler — a single check on mount, with
  nothing to retry or cancel — and is not the pattern to copy.
- **Styling** is CSS Modules: `Component.module.css` beside `Component.tsx`.
  Colours, fonts and shadows come from the custom properties in `index.css`,
  which also defines their dark-scheme values; add a token there rather than
  hard-coding a colour in a module.
- **API types** are written by hand until #27 generates them from the
  backend's OpenAPI specification. They go in the feature's `types/` (as in
  `games`), unless the feature has a single small response shape, which can sit
  beside its request in `api/` (as in `system-health`). The backend is
  authoritative — keep them in step with its DTOs.
- **Chess logic** that decides canonical state belongs to the backend. The
  frontend uses `chess.js` only to replay moves the backend has already
  validated, and only in `features/games/replay.ts`.
- **Tests** sit beside the file they test (`*.test.ts(x)`).
