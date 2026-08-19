# CLAUDE.md

## Purpose

This file defines how Claude should work in this repository.

Before implementing a feature or making an architectural decision:

1. Read `CONTEXT.md`.
2. Inspect the relevant existing code.
3. Understand the affected domain and service boundaries.
4. Prefer the simplest design that satisfies the current requirement.
5. Do not introduce future complexity unless there is a clear current need.

`CONTEXT.md` is the source of truth for the product direction and architectural intent.

---

## Development Philosophy

This project should start simple and evolve deliberately.

Prefer:

* A modular monolith over premature microservices.
* Clear service boundaries in code before physical service extraction.
* Feature-first organisation.
* Explicit domain models.
* Deterministic chess logic.
* Small, testable components.
* Simple infrastructure.
* Incremental delivery through vertical slices.

Avoid:

* Premature microservices.
* Distributed transactions.
* Kafka/RabbitMQ unless there is a demonstrated need.
* Kubernetes for the initial product.
* Generic abstractions created only for hypothetical future requirements.
* Large page or controller files containing unrelated responsibilities.
* Business logic in controllers.
* Business logic in React components.
* AI-generated chess decisions where deterministic validation is possible.

---

# Working Style

## Challenge assumptions

Do not automatically agree with requested implementation approaches.

If an approach:

* introduces unnecessary complexity;
* creates problematic coupling;
* conflicts with the architecture;
* has security implications;
* makes future work significantly harder; or
* has a simpler alternative;

explain the trade-off and recommend the stronger approach.

The developer makes the final decision.

---

## Small changes

For small, isolated changes:

* inspect the relevant code;
* make the change directly;
* add or update appropriate tests;
* run relevant checks.

Do not create unnecessary planning documents for trivial work.

---

## Large features

For features that:

* affect multiple modules;
* change architecture;
* introduce new infrastructure;
* introduce a new external integration;
* alter persistence models significantly; or
* require a new service;

use a deliberate planning workflow before implementation.

When Superpowers or an equivalent structured development skill is available, use it for these larger features.

The plan should identify:

* the user-facing goal;
* affected modules;
* domain changes;
* API changes;
* persistence changes;
* tests;
* migrations;
* risks;
* what is explicitly out of scope.

Do not begin a large refactor merely because an adjacent feature exposes imperfect code.

---

# Model Selection

Use the least expensive/capable model that can perform the task reliably.

Model choice should be based primarily on:

* reasoning complexity;
* ambiguity;
* architectural impact;
* number of affected components;
* expected task duration;
* degree of autonomy required;
* difficulty of validating the result.

Do not select a more powerful model merely because a task is important.

The default model for normal development work is **Sonnet**.

---

## Haiku

Use Haiku for small, mechanical, low-risk tasks where the desired change is already clear and little reasoning is required.

Examples:

* locating files or symbols;
* summarising existing code;
* simple renames;
* straightforward formatting changes;
* updating repetitive boilerplate;
* extracting information from logs;
* simple documentation lookups;
* other narrow subtasks delegated by a more capable model.

Do not use Haiku for:

* architectural decisions;
* domain modelling;
* debugging ambiguous failures;
* significant refactors;
* implementing new business behaviour;
* security-sensitive changes;
* migrations.

When uncertain between Haiku and Sonnet, use Sonnet.

---

## Sonnet

**Sonnet is the default implementation model.**

Use Sonnet for the majority of normal software-development work.

Examples:

* implementing a well-understood feature;
* adding REST endpoints;
* creating React components;
* writing hooks;
* adding database entities and repositories;
* implementing known business rules;
* writing tests;
* fixing straightforward bugs;
* refactoring a contained area;
* implementing a plan that has already been designed;
* reviewing normal code changes.

Typical chess-project examples include:

* implementing `Player`;
* adding a PGN upload endpoint;
* building the game list;
* creating the game viewer;
* implementing a known `GameImport` state transition;
* adding Flyway migrations;
* wiring a repository to an existing domain model.

Sonnet should normally implement work even when Opus or Fable was used to design the solution.

Do not escalate solely because a change touches several files.

---

## Opus

Use Opus when the task requires significant reasoning, contains meaningful ambiguity, or has architectural consequences.

Opus is the preferred model for **difficult problems that are still reasonably bounded**.

Examples:

* architecture and domain modelling;
* designing a new subsystem;
* deciding service boundaries;
* complex debugging where the cause is unknown;
* concurrency or transaction problems;
* difficult persistence modelling;
* security-sensitive design;
* designing API contracts shared by multiple components;
* evaluating competing technical approaches;
* significant refactors with unclear consequences;
* reviewing architecture before implementing a large feature.

Typical chess-project examples include:

* designing the `GameImport` state machine;
* deciding how canonical PGN and derived move data should be stored;
* designing position indexing for opponent preparation;
* designing the recognition-service API;
* modelling player identity and aliases across external databases;
* deciding whether a component should become a microservice;
* investigating a difficult PGN reconstruction bug;
* designing permissions for club/shared games.

Prefer the pattern:

```text
Opus
  ↓
understand / design / plan
  ↓
Sonnet
  ↓
implement
```

when the implementation itself is straightforward after the difficult decisions have been made.

Opus may also implement the change directly when reasoning and implementation are tightly coupled.

---

## Fable

Use Fable selectively for the most complex **long-horizon** work.

Fable should not simply mean "more powerful Opus."

Prefer Fable when the task combines several of the following:

* spans multiple subsystems;
* requires a substantial sequence of dependent decisions;
* requires extensive repository exploration;
* is expected to involve many implementation and verification steps;
* requires coordinating several specialised subtasks;
* has significant architectural uncertainty;
* involves a large migration or redesign;
* benefits materially from sustained autonomous work;
* requires repeatedly implementing, testing, evaluating and correcting its own work.

Typical examples include:

* implementing the complete scoresheet-import feature across backend, frontend, persistence and recognition integration;
* migrating a major subsystem to a new architecture;
* extracting the notation-recognition module into an independently deployed service;
* introducing the complete reference-game architecture with multiple providers;
* performing a large repository-wide architectural restructuring;
* implementing a substantial opponent-preparation system spanning game search, position indexing, APIs and frontend workflows.

A task should **not** use Fable merely because:

* it contains difficult reasoning;
* it modifies an important class;
* it has a tricky bug;
* it requires an architectural decision;
* it affects several files.

Those are normally Opus tasks.

A useful distinction is:

```text
Sonnet
    normal implementation

Opus
    difficult reasoning

Fable
    difficult reasoning
    + broad scope
    + long-running execution
    + many dependent steps
```

---

# Model Escalation

Do not repeatedly struggle with the wrong model.

Escalate from Haiku to Sonnet when:

* reasoning beyond a mechanical transformation is required;
* assumptions must be made;
* business behaviour needs to be understood.

Escalate from Sonnet to Opus when:

* the cause of a problem remains unclear after reasonable investigation;
* multiple plausible designs have meaningful trade-offs;
* the task reveals architectural uncertainty;
* implementation requires substantial reasoning that was not apparent initially.

Escalate from Opus to Fable when:

* solving the problem has expanded into a long-running multi-stage project;
* several dependent subsystems must be coordinated;
* extensive autonomous implementation and verification would materially improve the outcome.

Do not escalate merely because an initial attempt failed.

First determine whether the failure came from:

* an incorrect assumption;
* missing repository context;
* inadequate tests;
* an external dependency;
* an incorrect implementation;

rather than model capability.

---

# Model De-escalation

A powerful model does not need to perform every subsequent task.

After Fable or Opus has produced a clear design or implementation plan, delegate well-defined implementation tasks to Sonnet where practical.

For example:

```text
Fable / Opus
    ↓
design GameImport workflow
    ↓
define tasks and contracts
    ↓
Sonnet
    ├── implement backend domain
    ├── implement API
    ├── implement frontend review UI
    └── write tests
```

Use Haiku for narrow mechanical subtasks where appropriate.

---

# Superpowers and Model Choice

Model choice and development workflow are separate decisions.

Using Superpowers does not automatically require Fable.

For a significant but bounded feature:

```text
Superpowers
+
Opus for design
+
Sonnet for implementation
```

is often appropriate.

Use Fable with Superpowers when the feature itself is sufficiently large, interconnected and long-running to justify it.

Examples:

### Small feature

```text
Add filtering to the games page
→ Sonnet
→ no Superpowers required
```

### Medium feature

```text
Add PGN import and validation
→ Sonnet
→ use structured planning if implementation becomes cross-cutting
```

### Complex feature

```text
Design the GameImport review workflow
→ Opus + Superpowers
→ Sonnet for well-defined implementation tasks
```

### Long-horizon feature

```text
Implement scoresheet recognition end-to-end,
including upload, recognition, chess validation,
review UI, correction and persistence
→ Fable + Superpowers
→ delegate bounded implementation tasks where useful
```

---

# Before Starting Work

For non-trivial tasks, determine the appropriate model before implementation.

Do not spend significant time discussing model selection with the developer unless:

* the requested model appears inappropriate;
* the model materially affects cost or feasibility;
* the required model is unavailable.

Otherwise select the appropriate level according to these rules and proceed.

# Repository Architecture

The intended high-level repository structure is:

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
├── CLAUDE.md
└── CONTEXT.md
```

Not every directory needs to exist immediately.

Create components only when they become necessary.

---

# Core Backend

## Technology

The core application uses:

* Java 25
* Spring Boot 4.x
* Maven
* PostgreSQL 18
* Flyway
* JUnit
* Testcontainers

The core application is initially a modular monolith.

Do not split a module into a deployable microservice without a concrete reason.

---

## Backend module structure

Organise code primarily by domain/feature rather than technical layer.

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

Do not organise the entire application like:

```text
controller/
service/
repository/
model/
```

Domain ownership should be visible from the package structure.

---

## Layer responsibilities

### API

Responsible for:

* HTTP concerns;
* request validation;
* DTO conversion;
* authentication context;
* response codes.

Controllers should be thin.

### Application

Responsible for:

* use cases;
* orchestration;
* transactions;
* interaction between domain objects and infrastructure.

### Domain

Responsible for:

* business rules;
* domain entities;
* value objects;
* domain-specific validation.

Domain code should avoid Spring dependencies where practical.

### Persistence

Responsible for:

* JPA entities where required;
* repositories;
* database mappings;
* persistence-specific queries.

Do not expose persistence entities directly through API responses.

---

# Frontend

## Technology

Use:

* React
* TypeScript
* Vite
* Yarn

Prefer a feature-first structure:

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

Feature-specific components belong with their feature.

Only genuinely reusable components belong in shared directories.

---

## React components

Avoid large page components.

Pages should primarily:

* compose components;
* coordinate page-level state;
* invoke feature hooks.

Extract:

* complex UI sections;
* reusable behaviour;
* API state;
* domain transformations.

Avoid unnecessary abstraction for very small components.

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

Use explicit actions only where the operation is genuinely behavioural, for example:

```text
POST /game-imports/{id}/confirm
```

Do not expose internal database structure through API design.

Maintain an OpenAPI specification generated from or aligned with the backend API.

The frontend should eventually use generated API types/client code rather than manually duplicating backend DTOs.

---

# Persistence

PostgreSQL is the primary transactional database.

Use relational columns for data that is commonly:

* filtered;
* joined;
* constrained;
* sorted;
* referenced.

Use `JSONB` selectively for flexible chess-specific derived structures.

Do not default everything to JSON.

---

## Game persistence

A confirmed game has one canonical PGN.

Store searchable metadata separately, including where applicable:

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

Derived structures may include:

* parsed moves;
* opening information;
* position hashes;
* engine analysis metadata.

Derived structures must not silently become competing sources of truth with the canonical game.

---

## Images

Do not store scoresheet image binary data directly in PostgreSQL.

Store images in object storage.

Store metadata and object identifiers/URLs in the core database.

Local development may use an S3-compatible local object store.

Production storage may use AWS S3 or another compatible provider.

---

# Chess Rules and PGN Handling

Chess legality and PGN construction must be deterministic.

Do not rely on an LLM to:

* decide whether a move is legal;
* invent missing moves;
* silently repair a game;
* create the canonical PGN without deterministic validation.

Use a chess rules library for:

* legal move generation;
* SAN parsing;
* board reconstruction;
* FEN handling;
* PGN validation.

AI output is evidence, not truth.

---

# Scoresheet Recognition

The recognition workflow is:

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

A `GameImport` and a `Game` are different concepts.

A `GameImport` may be incomplete, ambiguous, incorrect or awaiting review.

A `Game` represents a confirmed chess game.

Never save an AI transcription directly as a confirmed `Game`.

---

## GameImport lifecycle

Expected states include:

```text
UPLOADED
PROCESSING
READY_FOR_REVIEW
CONFIRMED
FAILED
```

Exact states may evolve with the implementation.

State transitions should be explicit.

---

## Recognition boundary

Define recognition behind an application interface such as:

```java
public interface NotationRecognizer {
    RecognitionResult recognize(Scoresheet scoresheet);
}
```

The initial implementation may be fake/test-based.

Later implementations may include:

```text
MultimodalLlmNotationRecognizer
RemoteNotationServiceRecognizer
```

The rest of the core application must not depend on a specific AI provider.

---

## Recognition results

Recognition should preserve uncertainty.

A move recognition may contain:

* raw recognised text;
* candidate SAN strings;
* confidence;
* alternative candidates;
* location within the source image.

Do not throw away the model's original prediction when the user corrects it.

Corrections may become useful labelled data for future recognition improvements.

---

# Notation Recognition Service

Do not create this deployable service until there is value in extracting it.

When extracted, its likely stack is:

```text
Python
FastAPI
```

Its responsibility is narrow:

> Given one or more scoresheet images, return a structured transcription with uncertainty.

It does not own:

* users;
* players;
* games;
* clubs;
* canonical PGNs;
* permissions.

The service should remain replaceable.

---

# Reference Games and Mega Database

Do not couple opponent preparation directly to Mega Database.

Use an abstraction such as:

```java
public interface ReferenceGameProvider {
    GameSearchResult search(GameSearchCriteria criteria);
}
```

Possible future providers include:

```text
InternalGameProvider
MegaDatabaseProvider
LichessProvider
OtherPgnDatabaseProvider
```

Opponent-preparation code should depend on the abstraction rather than a specific provider.

Do not reverse-engineer external private APIs without an explicit architectural decision.

Consider licensing and redistribution rights before persisting or exposing third-party database content.

---

# Opponent Preparation

Opponent preparation should eventually answer questions such as:

* What does this opponent usually play?
* What have they played recently?
* What do they play as White?
* What do they play as Black?
* How frequently do they reach a given position?
* What do they usually play from that position?
* Which parts of their repertoire intersect with the user's repertoire?

Prefer chess-position-based analysis over naive PGN text searching.

Do not optimise for millions of reference games until a real dataset requires it.

---

# Microservices

A component should become a microservice when independent deployment provides a real advantage.

Good future candidates are:

### Notation recognition

Reasons:

* Python/ML ecosystem;
* external AI dependencies;
* potentially different compute requirements.

### Reference-game search

Reasons:

* potentially very large datasets;
* specialised indexing;
* independent external integrations;
* different scaling characteristics.

### Engine analysis

Possible future extraction because Stockfish analysis is CPU-intensive.

The following should remain in the core application initially:

* users;
* clubs;
* memberships;
* players;
* personal games;
* game imports;
* preparation configuration;
* permissions.

---

# Infrastructure

Local development should use Docker Compose for infrastructure dependencies.

Avoid requiring containers for every application process if running the process directly improves developer experience.

Do not add Kubernetes initially.

Do not add a message broker until asynchronous workload requirements justify one.

---

# Testing

Tests are required for meaningful business behaviour.

## Backend

Use:

* unit tests for domain behaviour;
* application/service tests for use cases;
* integration tests for persistence;
* Testcontainers for database integration tests;
* API tests for important HTTP workflows.

Particularly important tests include:

* PGN validation;
* move legality;
* GameImport state transitions;
* recognition ambiguity handling;
* corrected move handling;
* Game creation after confirmation.

## Frontend

Test meaningful user workflows and complex components.

Avoid tests that merely duplicate implementation details.

## Python recognition service

Use `pytest`.

Recognition provider integrations should be abstracted so tests do not depend on external AI APIs.

---

# Database Migrations

Use Flyway.

Never rely on Hibernate auto-DDL for production schema management.

Schema changes should include migrations in the same change as the corresponding application code.

---

# External APIs

Wrap external providers behind internal interfaces.

Never spread provider-specific SDK objects throughout the domain.

Configuration such as:

* API keys;
* tokens;
* credentials;

must come from environment-specific secret management.

Never commit credentials.

---

# Code Quality

Prefer readable code over clever code.

Use descriptive names.

Keep methods focused.

Avoid:

* deeply nested conditionals;
* premature generic frameworks;
* utility classes containing unrelated behaviour;
* huge service classes;
* massive React components;
* duplicated domain logic between frontend and backend.

If duplicated logic is security-sensitive or determines canonical chess state, the backend is authoritative.

---

# Documentation

Update `CONTEXT.md` when a decision materially changes:

* product scope;
* architecture;
* domain ownership;
* persistence approach;
* service boundaries;
* major workflow.

Do not use `CONTEXT.md` as a running implementation log.

Important architectural decisions that require detailed reasoning may later be captured as ADRs under:

```text
docs/adr/
```

---

# Initial Delivery Priority

Optimise for this first complete workflow:

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

Do not allow future opponent-preparation or Mega Database requirements to block delivery of this first vertical slice.
