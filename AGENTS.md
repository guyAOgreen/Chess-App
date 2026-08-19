# AGENTS.md

## Purpose

This file defines how Codex and other coding agents should work in this repository.

Before implementing a feature or making an architectural decision:

1. Read `CONTEXT.md`.
2. Inspect the relevant existing code.
3. Understand the affected domain and service boundaries.
4. Check for more specific `AGENTS.md` files in the directory tree.
5. Prefer the simplest design that satisfies the current requirement.
6. Do not introduce future complexity unless there is a clear current need.

`CONTEXT.md` is the source of truth for product direction and architectural intent.

`AGENTS.md` defines implementation expectations.

---

# Development Philosophy

This project should start simple and evolve deliberately.

Prefer:

* a modular monolith over premature microservices;
* clear service boundaries in code before physical service extraction;
* feature-first organisation;
* explicit domain models;
* deterministic chess logic;
* small, testable components;
* simple infrastructure;
* incremental delivery through vertical slices.

Avoid:

* premature microservices;
* distributed transactions;
* Kafka or RabbitMQ without a demonstrated need;
* Kubernetes for the initial product;
* abstractions created only for hypothetical future requirements;
* large page or controller files containing unrelated responsibilities;
* business logic in controllers;
* business logic in React components;
* AI-generated chess decisions where deterministic validation is possible.

---

# Working Style

## Inspect before changing

Do not assume the repository follows the intended architecture perfectly.

Before making changes:

* inspect relevant files;
* inspect nearby tests;
* inspect existing conventions;
* identify the current behaviour;
* understand how the affected code is used.

Do not blindly copy an existing pattern if that pattern conflicts with this file or `CONTEXT.md`.

Existing code is evidence, not automatically the preferred design.

---

## Challenge assumptions

Do not automatically agree with a requested implementation approach.

If an approach:

* introduces unnecessary complexity;
* creates problematic coupling;
* conflicts with the architecture;
* has security implications;
* makes future work significantly harder;
* creates avoidable operational complexity; or
* has a substantially simpler alternative;

explain the trade-off and recommend the stronger approach.

The developer makes the final decision.

---

# Task Size

## Small changes

For small, isolated changes:

1. inspect the relevant code;
2. implement the change;
3. add or update appropriate tests;
4. run relevant checks;
5. report what changed.

Do not create unnecessary planning artifacts for trivial changes.

Examples include:

* a contained bug fix;
* adding a field to an existing DTO;
* a small UI adjustment;
* adding a straightforward validation rule;
* a focused refactor.

---

## Large features

Treat work as a large feature when it:

* affects multiple modules;
* changes architecture;
* introduces infrastructure;
* introduces an external integration;
* alters persistence models significantly;
* changes important domain behaviour;
* introduces asynchronous processing;
* requires a new service;
* substantially changes frontend and backend together.

For large features, inspect and plan before implementing.

The plan should identify:

* user-facing goal;
* affected modules;
* domain changes;
* API changes;
* persistence changes;
* frontend changes;
* tests;
* database migrations;
* operational implications;
* risks;
* explicitly excluded work.

Prefer completing one coherent vertical slice over creating incomplete infrastructure across many future features.

---

# Repository Architecture

The intended high-level structure is:

```text
/
├── apps/
│   └── web/
│
├── services/
│   ├── core/
│   └── notation-recognition/
│
├── packages/
├── infra/
├── AGENTS.md
├── CLAUDE.md
└── CONTEXT.md
```

Not every directory needs to exist immediately.

Create components only when they become necessary.

---

# Core Backend

## Technology

The core application uses:

* Java 25;
* Spring Boot 4.x;
* Maven;
* PostgreSQL 18;
* Flyway;
* JUnit;
* Testcontainers.

The core application is initially a modular monolith.

Do not split a module into a deployable microservice without a concrete reason.

---

# Backend Organisation

Organise code primarily by domain or feature rather than global technical layer.

Prefer:

```text
com.<project>
├── game/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── persistence/
│
├── player/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── persistence/
│
├── gameimport/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── persistence/
│
├── preparation/
├── club/
├── user/
└── shared/
```

Avoid organising the whole application as:

```text
controller/
service/
repository/
model/
```

Domain ownership should be visible from package structure.

---

# Backend Responsibilities

## API

Responsible for:

* HTTP concerns;
* request validation;
* authentication context;
* request/response DTOs;
* mapping;
* response codes.

Controllers should remain thin.

Do not implement domain logic in controllers.

---

## Application

Responsible for:

* use cases;
* orchestration;
* transactions;
* interaction between domain objects and infrastructure;
* coordinating module boundaries.

Application services should express user/application actions rather than becoming generic containers for unrelated business logic.

---

## Domain

Responsible for:

* business rules;
* entities;
* value objects;
* domain invariants;
* state transitions;
* chess-domain-specific concepts.

Keep Spring dependencies out of domain code where practical.

Prefer behaviour-rich domain objects where that improves correctness.

Do not create setters simply to satisfy persistence.

---

## Persistence

Responsible for:

* JPA mappings;
* repositories;
* persistence queries;
* database-specific representations.

Do not expose persistence entities directly through HTTP APIs.

Do not allow persistence concerns to determine the public domain model unnecessarily.

---

# DTOs

Use explicit request and response DTOs.

Prefer Java records for DTOs unless mutability is required.

For example:

```java
public record CreateGameRequest(
        String pgn
) {
}
```

Do not return JPA entities from controllers.

Do not reuse one DTO for several unrelated API operations merely to reduce the number of classes.

---

# Frontend

## Technology

Use:

* React;
* TypeScript;
* Vite;
* Yarn.

Prefer feature-first organisation:

```text
src/
├── features/
│   ├── games/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── api/
│   │   ├── types/
│   │   └── pages/
│   │
│   ├── game-imports/
│   ├── preparation/
│   ├── players/
│   └── clubs/
│
├── components/
│   └── shared/
│
├── hooks/
│   └── shared/
│
├── lib/
└── app/
```

Feature-specific components belong with the feature.

Only genuinely cross-feature components should be placed in shared directories.

---

# React Components

Avoid large page components.

Pages should primarily:

* compose feature components;
* coordinate page-level state;
* invoke feature hooks.

Extract:

* substantial UI sections;
* reusable interactions;
* server/API state;
* domain transformations;
* complex forms.

Do not extract tiny components solely to reduce line count.

Prefer useful boundaries over arbitrary fragmentation.

Do not put API calls directly throughout presentation components.

---

# API Design

Use REST-oriented APIs unless there is a strong reason otherwise.

Prefer resources such as:

```text
/games
/players
/game-imports
/preparations
/clubs
```

Use explicit actions only for genuinely behavioural operations, for example:

```text
POST /game-imports/{id}/confirm
```

Do not expose internal database structure through API design.

Maintain an OpenAPI specification generated from or aligned with the backend API.

The frontend should eventually consume generated API types or client code instead of manually duplicating contracts.

---

# Persistence

PostgreSQL is the primary transactional database.

Use relational columns for information commonly:

* filtered;
* joined;
* constrained;
* sorted;
* referenced.

Use `JSONB` selectively for flexible chess-specific derived data.

Do not default structured domain data to JSON.

---

# Database Migrations

Use Flyway for schema management.

Do not rely on Hibernate schema auto-generation as the source of truth.

Every schema change must include the corresponding migration.

Do not silently modify an existing migration that may already have been applied.

Create a new migration instead.

---

# Game Persistence

A confirmed game has one canonical PGN.

Store commonly searched metadata separately, including where applicable:

* white player;
* black player;
* event;
* site;
* date;
* round;
* result;
* ratings;
* ECO;
* source.

Derived data may include:

* parsed moves;
* opening information;
* position hashes;
* engine analysis.

Derived structures must not become competing sources of truth with the canonical game.

When derived information can be recreated from the PGN, make that relationship clear.

---

# Chess Logic

Chess legality and PGN construction must be deterministic.

Do not use an LLM to:

* determine whether a move is legal;
* invent missing moves;
* silently repair a game;
* generate canonical game state without deterministic validation.

Use an appropriate chess library for:

* legal move generation;
* SAN parsing;
* move application;
* board reconstruction;
* FEN handling;
* PGN parsing;
* PGN validation.

Wrap third-party chess-library behaviour where doing so prevents it leaking throughout the domain.

---

# Scoresheet Recognition

The intended workflow is:

```text
scoresheet image
    ↓
recognition
    ↓
candidate transcription
    ↓
deterministic chess validation
    ↓
player review
    ↓
confirmed moves
    ↓
canonical PGN
    ↓
Game
```

`GameImport` and `Game` are different concepts.

A `GameImport` may be:

* incomplete;
* ambiguous;
* incorrect;
* processing;
* awaiting review.

A `Game` represents a user-confirmed canonical chess game.

Never save an AI transcription directly as a confirmed `Game`.

---

# GameImport

Likely lifecycle states include:

```text
UPLOADED
PROCESSING
READY_FOR_REVIEW
CONFIRMED
FAILED
```

The precise state machine should be implemented deliberately.

State transitions should be explicit and tested.

Invalid transitions should fail predictably.

Do not scatter state-transition rules through controllers or unrelated services.

---

# Recognition Boundary

Define recognition behind a core application interface, conceptually:

```java
public interface NotationRecognizer {

    RecognitionResult recognize(Scoresheet scoresheet);
}
```

Possible implementations may eventually include:

```text
FakeNotationRecognizer
MultimodalLlmNotationRecognizer
RemoteNotationServiceRecognizer
```

The rest of the core application should not know which AI provider performs recognition.

---

# Recognition Results

Preserve uncertainty.

Recognition results may include:

* raw recognised text;
* candidate SAN strings;
* confidence values;
* alternative candidates;
* coordinates or regions within the scoresheet image.

Do not overwrite the original recognition when the player corrects a move.

Preserve:

```text
recognised result
+
player-confirmed result
```

Corrections may later form useful labelled training data.

---

# Images

Do not store scoresheet image bytes in PostgreSQL.

Store images in object storage.

Persist only required metadata and object identifiers in the application database.

Local development may use an S3-compatible object store.

Production may use AWS S3 or another compatible provider.

Keep object-storage access behind an internal abstraction.

---

# Notation Recognition Service

Do not create a separately deployed recognition service before it is useful.

When extracted, its expected stack is:

```text
Python
FastAPI
```

Its responsibility should remain narrow:

> Given scoresheet images, return structured recognition results with uncertainty.

It should not own:

* users;
* players;
* clubs;
* application permissions;
* canonical games;
* canonical PGNs.

The service should remain replaceable.

---

# External AI Providers

Treat external AI services as infrastructure dependencies.

Wrap provider-specific APIs behind internal interfaces.

Do not expose provider SDK objects to domain or API layers.

Prompts should be versioned or otherwise traceable where recognition behaviour depends on them.

Store sufficient metadata to understand how a recognition was produced when useful, for example:

* provider;
* model;
* prompt/version;
* timestamp.

Never commit API credentials.

---

# Reference Games

Do not couple opponent preparation directly to Mega Database or another single provider.

Use an abstraction conceptually similar to:

```java
public interface ReferenceGameProvider {

    GameSearchResult search(GameSearchCriteria criteria);
}
```

Possible providers include:

```text
InternalGameProvider
MegaDatabaseProvider
LichessProvider
OtherPgnDatabaseProvider
```

Preparation logic should depend on the abstraction rather than provider-specific APIs.

---

# Mega Database

Mega Database is a possible future source, not a dependency of the initial system.

Do not:

* import millions of games into the primary application database without a demonstrated requirement;
* design core domain models around ChessBase-specific formats;
* reverse-engineer private APIs as part of ordinary implementation;
* assume redistribution rights.

Any Mega Database integration must account for licensing and permitted use.

---

# Opponent Preparation

Opponent preparation should eventually answer questions such as:

* What does this player normally play?
* What have they played recently?
* What do they play as White?
* What do they play as Black?
* How often do they reach a given position?
* What do they play next from that position?
* Where does their repertoire intersect with the user's repertoire?

Prefer position-based chess analysis over naive text searching.

Do not optimise for millions of reference games until realistic performance requirements exist.

---

# Microservices

A module should become an independent service only when independent deployment provides a concrete advantage.

Potential future candidates:

## Notation recognition

Possible reasons:

* Python/ML ecosystem;
* external AI dependencies;
* different compute characteristics;
* independent scaling.

## Reference-game search

Possible reasons:

* large datasets;
* specialised indexing;
* provider integrations;
* independent scaling characteristics.

## Engine analysis

Possible reasons:

* CPU-intensive Stockfish workloads;
* asynchronous processing;
* separate scaling requirements.

Keep the following in the core application initially:

* users;
* clubs;
* memberships;
* players;
* personal games;
* game imports;
* preparation configuration;
* permissions.

---

# Asynchronous Work

Do not introduce asynchronous architecture merely because a task could theoretically run asynchronously.

Introduce background work when:

* processing is slow enough to affect HTTP usability;
* retries are required;
* processing has independent lifecycle/state;
* workload scaling differs materially from request traffic.

`GameImport` recognition is a likely future asynchronous workflow.

When asynchronous processing is introduced:

* persist explicit state;
* design retries deliberately;
* make processing idempotent where possible;
* expose meaningful status to the client;
* handle failures explicitly.

Do not add a message broker until the required processing model justifies one.

---

# Infrastructure

Use Docker Compose for local infrastructure dependencies.

Do not require application processes themselves to run in containers if local execution offers a better development workflow.

Do not introduce Kubernetes for the initial product.

Infrastructure should remain reproducible and documented.

---

# Testing

Meaningful business behaviour requires tests.

Do not treat test creation as optional work after implementation.

---

## Backend tests

Use:

* unit tests for domain behaviour;
* application/service tests for use cases;
* repository integration tests;
* Testcontainers for database-backed tests;
* API tests for important HTTP workflows.

Important areas include:

* PGN validation;
* chess move legality;
* GameImport state transitions;
* recognition ambiguity;
* user corrections;
* creation of a Game after confirmation;
* database constraints;
* player matching where introduced.

Prefer testing behaviour over implementation details.

---

## Frontend tests

Test important workflows and components where regressions would matter.

Particularly valuable workflows include:

* game import;
* game navigation;
* scoresheet review;
* correcting uncertain moves;
* confirming an import.

Avoid excessive tests for static presentational markup.

---

## Recognition service tests

When the Python service exists, use `pytest`.

External AI calls must be abstracted so ordinary automated tests do not require live API requests.

Use representative fixture responses.

---

# Commands and Verification

Before declaring work complete, run the relevant repository checks where available.

Typical backend verification may include:

```bash
./mvnw test
```

or:

```bash
mvn test
```

Typical frontend verification may include:

```bash
yarn lint
yarn test
yarn build
```

Use the actual scripts defined by the repository rather than assuming these exact commands exist.

For a full-stack change, verify each affected application.

Do not claim tests passed unless they were actually run successfully.

If a check cannot be run, explain why.

---

# Error Handling

Prefer explicit domain/application errors over generic failures.

HTTP APIs should use appropriate status codes.

Do not expose:

* stack traces;
* database implementation details;
* credentials;
* internal provider responses;

through public API errors.

Error responses should be useful to the frontend without revealing unnecessary internals.

---

# Security

Treat all uploaded files and external input as untrusted.

Validate:

* file types;
* file sizes;
* request payloads;
* identifiers;
* permissions.

Do not trust client-supplied ownership or user IDs when those values can be derived from authentication.

Never commit:

* passwords;
* API keys;
* tokens;
* private keys;
* connection credentials.

---

# Code Quality

Prefer readable code over clever code.

Use descriptive names.

Keep methods and components focused.

Avoid:

* deeply nested conditionals;
* premature generic frameworks;
* utility classes with unrelated responsibilities;
* huge service classes;
* massive React components;
* duplicated canonical business rules;
* abstractions with only hypothetical consumers.

If logic determines canonical chess state or security-sensitive behaviour, the backend is authoritative.

---

# Refactoring

Refactor when it materially improves the change being made.

Do not perform unrelated repository-wide cleanup during a feature unless it is required to implement the feature safely.

If nearby technical debt is discovered but not required:

* leave it unchanged;
* mention it if materially relevant;
* do not silently expand the task.

---

# Dependencies

Before adding a dependency:

1. determine whether the standard library or an existing dependency already solves the problem;
2. verify that the dependency is actively maintained;
3. prefer well-established libraries;
4. minimise overlapping libraries for the same responsibility.

Do not implement complicated chess algorithms from scratch when a reliable chess library provides the required functionality.

Avoid adding frameworks to solve small problems.

---

# Documentation

Update `CONTEXT.md` when a decision materially changes:

* product scope;
* architecture;
* domain ownership;
* persistence approach;
* service boundaries;
* major workflows.

Do not use `CONTEXT.md` as an implementation diary.

Update `README.md` when developer setup or usage changes.

Important architectural decisions may be recorded under:

```text
docs/adr/
```

Use ADRs for decisions where retaining the reasoning will be valuable.

---

# Git Changes

Keep changes scoped to the task.

Do not modify unrelated files.

Do not rewrite unrelated formatting throughout the repository.

Do not remove user work unless explicitly required.

When modifying existing code, preserve behaviour outside the requested scope unless the change deliberately alters it.

---

# Initial Delivery Priority

Optimise for the first complete vertical slice:

```text
enter/import PGN
    ↓
validate
    ↓
save
    ↓
view game
```

Then:

```text
upload scoresheet
    ↓
recognise moves
    ↓
review
    ↓
correct
    ↓
confirm
    ↓
save Game
```

Do not allow future opponent preparation, Mega Database integration, microservices, or custom machine-learning requirements to block delivery of these workflows.

---

# Definition of Done

A change is complete when, as applicable:

* required behaviour is implemented;
* architecture remains consistent with `CONTEXT.md`;
* relevant tests exist;
* relevant tests pass;
* database migrations are included;
* API contracts are updated;
* frontend and backend contracts agree;
* errors and edge cases are handled;
* no credentials are committed;
* relevant documentation is updated;
* unrelated behaviour has not been changed.

Before finishing, review the resulting diff as a whole rather than considering only individual edited files.
