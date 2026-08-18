# Chess Prep

Chess game storage, scoresheet digitisation and opponent preparation.

See [CONTEXT.md](CONTEXT.md) for product direction and architecture, and
[CLAUDE.md](CLAUDE.md) for development conventions.

## Layout

    apps/web/         React + TypeScript + Vite frontend
    services/core/    Java + Spring Boot backend
    infra/            local infrastructure (docker-compose)
    docs/             specs, plans and decision records

## Prerequisites

- JDK 25
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

The backend reads its datasource from `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`, defaulting to the
compose values, so no environment setup is needed for local work.

## Tests

    mvn -f services/core/pom.xml verify     # needs Docker for Testcontainers
    cd apps/web && yarn test
    cd apps/web && yarn lint

## Current state

This is the project skeleton only. There is no domain code yet — no games,
players or PGN handling. See the open issues and milestones for what comes
next.
