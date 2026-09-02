# Chess Prep

Chess game storage, scoresheet digitisation and opponent preparation.

See [CONTEXT.md](CONTEXT.md) for product direction and architecture,
[CLAUDE.md](CLAUDE.md) for development conventions, and
[docs/glossary.md](docs/glossary.md) for the chess terminology used throughout.

## Layout

    apps/web/         React + TypeScript + Vite frontend
    services/core/    Java + Spring Boot backend
    infra/            local infrastructure (docker-compose)
    docs/             specs, plans and decision records

## Prerequisites

- JDK 25 — `JAVA_HOME` must point at it. The pom targets release 25, so an
  older JDK fails with `error: release version 25 not supported`.
- Maven 3.9+
- Node 22+ and Yarn 1.x
- Docker

## Running locally

Start the database:

    docker compose -f infra/docker-compose.yml up -d

PostgreSQL is published on **5433**, not 5432. A native PostgreSQL install on
the host would otherwise shadow the container, and a client connecting from the
host would silently reach that instance instead — failing with an
authentication error rather than a connection refusal.

Start the backend on port 8080:

    mvn -f services/core/pom.xml spring-boot:run

Start the frontend:

    cd apps/web && yarn install && yarn dev

The frontend proxies `/api` and `/actuator` to the backend, so no CORS
configuration is needed in development. The home page reports the backend's
health, which is the quickest check that all three pieces are talking.

The frontend also uses browser (not hash) routing for its own paths, such as
`/games/{id}`. Vite's dev server already rewrites unknown application paths
to `index.html`, but a static host serving the production build must be
configured to do the same — while still leaving `/api/*` (and `/actuator/*`)
to the backend. Without that rewrite rule, in-app navigation works, but
refreshing or sharing a viewer URL returns the host's own 404 instead of the
app.

The backend reads its datasource from `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`, defaulting to the
compose values, so no environment setup is needed for local work.

### Profiles

`mvn spring-boot:run` activates the `local` profile, which turns on full
Actuator health detail. By default — and therefore in any deployed environment —
`/actuator/health` returns the overall status only, with no datasource, disk or
SSL detail, because nothing authenticates callers yet.

## Tests

    mvn -f services/core/pom.xml verify     # needs Docker for Testcontainers
    cd apps/web && yarn test
    cd apps/web && yarn lint

## Current state

This is the project skeleton only. There is no domain code yet — no games,
players or PGN handling. See the open issues and milestones for what comes
next.
