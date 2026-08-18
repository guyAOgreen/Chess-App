# Repository Scaffolding — Design

Date: 2026-08-18
Status: Approved

## Goal

Create the project skeleton described in `CONTEXT.md` under "Current First Task",
stopping short of the first vertical slice.

A developer cloning this repository should be able to start the database, start
the backend, start the frontend, and see the frontend report the backend's
health. Nothing more.

This is deliberately **skeleton only**. No domain code, no `Player`, no `Game`,
no PGN handling. Those follow as separate work.

## Success criteria

1. `docker compose -f infra/docker-compose.yml up -d` starts PostgreSQL 18.
2. `mvn verify` in `services/core` compiles and passes an integration test that
   boots the Spring context against a real PostgreSQL via Testcontainers.
3. `yarn dev` in `apps/web` serves the frontend, which fetches backend health
   through the Vite dev proxy and renders the result.
4. `yarn build` and `yarn test` pass in `apps/web`.

## Repository layout

```text
/
├── apps/
│   └── web/                    React + TypeScript + Vite (Yarn classic)
├── services/
│   └── core/                   Java 25 + Spring Boot 4.1.0 + Maven
├── infra/
│   └── docker-compose.yml      PostgreSQL 18
├── docs/
│   └── superpowers/specs/      design documents
├── .gitignore
├── CLAUDE.md
├── CONTEXT.md
└── README.md
```

Deliberately absent, per "create components only when they become necessary":

* `packages/` — nothing is shared between backend and frontend yet.
* `services/notation-recognition/` — Milestone 3.
* A root Maven aggregator pom — one backend service does not justify one, and
  introducing it later is a small change.

## Backend — `services/core`

### Build

* Standalone `pom.xml`, no parent aggregator.
* `spring-boot-starter-parent` version `4.1.0`.
* `groupId` `com.chessapp`, `artifactId` `core`.
* `java.version` 25.

### Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | HTTP layer |
| `spring-boot-starter-data-jpa` | persistence |
| `spring-boot-starter-validation` | request validation |
| `spring-boot-starter-actuator` | health and info endpoints |
| `postgresql` | JDBC driver (runtime) |
| `flyway-core`, `flyway-database-postgresql` | schema migrations |
| `spring-boot-starter-test` | test harness |
| `testcontainers-junit-jupiter`, `testcontainers-postgresql` | database integration tests |

### Packages

Base package `com.chessapp`, containing only `ChessAppApplication`.

Feature packages (`game/`, `player/`, `gameimport/`, `shared/`) and the
`api` / `application` / `domain` / `persistence` layering described in
`CLAUDE.md` are **not** created empty. The first real module establishes the
pattern. Empty directories are noise and git does not track them anyway.

### Health

Health is served by Actuator at `/actuator/health`, with the database health
indicator enabled and `show-details: always` for local development.

No hand-written health controller. Actuator already reports datasource
connectivity, and writing a controller to duplicate it would invent a module
with no domain behind it.

### Configuration

`application.yml` only. Datasource properties read from environment variables
with defaults matching `infra/docker-compose.yml`, so a local developer needs
no environment setup:

```text
SPRING_DATASOURCE_URL      default jdbc:postgresql://localhost:5432/chessapp
SPRING_DATASOURCE_USERNAME default chessapp
SPRING_DATASOURCE_PASSWORD default chessapp
```

Hibernate `ddl-auto` is `validate`. Flyway owns the schema; Hibernate never
generates it.

### Migrations

Flyway is enabled and configured, but **no migration files exist yet**. There is
no schema to create. The first migration lands in the same change as the first
entity, as required by `CLAUDE.md`.

### Tests

One test: `ApplicationContextIT`. It starts a PostgreSQL 18 container via
Testcontainers `@ServiceConnection` and boots the full Spring context.

This single test is the point of the backend skeleton. It proves the datasource,
JPA and Flyway are genuinely wired together, rather than merely declared in the
pom.

## Frontend — `apps/web`

### Build

Vite + React + TypeScript. Yarn classic (1.22), matching the installed toolchain.

### Structure

```text
src/
├── app/                 application shell, routing entry
├── features/            feature modules (empty for now)
├── components/shared/   genuinely reusable components
├── hooks/shared/        genuinely reusable hooks
└── lib/
    └── api.ts           thin typed fetch wrapper
```

### Behaviour

A single screen calls the backend health endpoint and renders the result:
reachable, database status, or the failure. This is the entire purpose of the
frontend skeleton — proving the wire end to end.

### Dev proxy

Vite proxies `/api` and `/actuator` to `http://localhost:8080`. This avoids
configuring CORS in development entirely.

### Tooling

* Vitest + React Testing Library, with one smoke test rendering the app shell.
* ESLint and Prettier.

## Infrastructure — `infra/docker-compose.yml`

PostgreSQL 18 only:

* named volume for data;
* port 5432;
* database `chessapp`, user `chessapp`, password `chessapp` — local development
  credentials, never used in a deployed environment.

No S3-compatible object storage yet. Scoresheet images arrive in Milestone 2,
and the store should be added with the feature that needs it.

## Repository hygiene

A root `.gitignore` covering Java/Maven (`target/`), Node (`node_modules/`,
`dist/`), IDE directories and local environment files.

`CLAUDE.md` and `CONTEXT.md` are currently untracked. They are the source of
truth for this project and are committed as part of this work.

## Risks

### Java 25 is not installed

The installed JDK is 21. The backend targets Java 25 by decision, so
`mvn verify` cannot be run on this machine until a JDK 25 is installed. The
backend will be delivered unverified, and this must be reported plainly rather
than assumed working.

The frontend and infrastructure can be verified immediately.

### Documentation drift

`CLAUDE.md` and `CONTEXT.md` name Spring Boot "4.x". The scaffolding pins
`4.1.0`. No documentation change is required.

## Out of scope

* Any domain code: `Player`, `Game`, `GameImport`, PGN parsing or validation.
* Authentication and authorisation.
* CI pipelines.
* OpenAPI specification and generated client types.
* Object storage.
* The Python notation-recognition service.
* Chess rules library selection.
