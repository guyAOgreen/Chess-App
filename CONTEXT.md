# Chess Prep — Project Context

## Overview

Chess Prep is a chess game storage, digitisation and opponent-preparation application.

The project began from a personal need to store chess games in a searchable database and convert handwritten chess scoresheets into PGNs.

The longer-term goal is to support opponent preparation using:

* personal games;
* club games;
* uploaded PGNs;
* external/reference chess databases;
* potentially ChessBase Mega Database or equivalent sources.

The application may later be made available to members of Obs Chess Club so that club members can prepare for opponents outside the club.

---

# Product Goals

## Personal game database

Allow a user to:

* store their chess games;
* import PGNs;
* enter games manually;
* view games on a chessboard;
* annotate games;
* search and filter games;
* analyse games;
* build a long-term record of their chess.

---

## Scoresheet digitisation

Allow a player to photograph a handwritten chess scoresheet and convert it into a valid PGN.

The system must assume that recognition can be wrong.

The player who uploads the scoresheet must review the recognised game before it becomes a confirmed game.

The intended workflow is:

```text
Photograph scoresheet
        ↓
Upload image
        ↓
AI recognises notation
        ↓
Chess rules validate/reconstruct game
        ↓
Uncertain moves highlighted
        ↓
Player reviews/corrects moves
        ↓
Player confirms game
        ↓
Canonical PGN created
        ↓
Game stored
```

Human review is a core product requirement, not an exceptional fallback.

---

# Recognition Philosophy

AI should solve the perception problem:

> What does the handwriting appear to say?

Deterministic chess software should solve the chess problem:

> Is this move legal and what position does it produce?

The AI should therefore return structured transcription information rather than simply returning a finished PGN.

For example:

```json
{
  "moveNumber": 12,
  "side": "WHITE",
  "rawText": "N?3",
  "candidates": [
    {
      "san": "Nf3",
      "confidence": 0.72
    },
    {
      "san": "Nh3",
      "confidence": 0.19
    }
  ]
}
```

Chess validation may eliminate impossible candidates.

If uncertainty remains, the user resolves it during review.

The system must not silently guess when the game cannot be reconstructed reliably.

---

# Recognition Data

Preserve both:

```text
original recognition
```

and:

```text
user-confirmed result
```

Do not overwrite the original AI result when the user corrects it.

This provides:

* an audit trail;
* debugging information;
* recognition-quality metrics;
* potential labelled training data.

A future custom handwriting-recognition model may be trained using corrections collected through normal application usage.

Training a custom model is not part of the initial scope.

---

# Core Domain Concepts

## User

An authenticated application user.

A user may eventually:

* own games;
* upload scoresheets;
* create preparation;
* belong to clubs.

---

## Player

A real-world chess player.

A `Player` does not need to be an application `User`.

This distinction is important because opponent preparation will frequently involve players who have never used the application.

Likely attributes include:

```text
id
displayName
fideId
federation
```

A player may eventually have aliases such as:

```text
FIDE name
alternative spelling
Lichess username
Chess.com username
```

---

## Game

A confirmed chess game.

A Game should contain canonical PGN plus searchable metadata.

Potential metadata includes:

```text
id
whitePlayerId
blackPlayerId
whiteRating
blackRating
event
site
round
playedAt
result
eco
source
pgn
```

Possible sources include:

```text
PERSONAL
CLUB
PGN_IMPORT
LICHESS
CHESS_COM
MEGA_DATABASE
OTHER
```

Exact source values may evolve.

---

## GameImport

Represents an attempt to create a game from a source that requires review or processing.

A `GameImport` is deliberately separate from `Game`.

A GameImport may contain:

* scoresheet images;
* AI transcription;
* candidate moves;
* confidence scores;
* validation issues;
* user corrections;
* generated PGN;
* processing status.

A GameImport is temporary/workflow-oriented.

A Game is canonical.

Possible lifecycle:

```text
UPLOADED
   ↓
PROCESSING
   ↓
READY_FOR_REVIEW
   ↓
CONFIRMED
   ↓
Game created
```

Failure may transition to:

```text
FAILED
```

The exact state machine should be designed when implementing the workflow.

---

## Club

Represents a chess club.

The initial real-world use case is Obs Chess Club.

Club functionality is not required for the first personal version.

Future functionality may include:

* membership;
* shared club games;
* club-specific permissions;
* opponent preparation;
* shared preparation resources.

Club membership should not be required for the underlying game-storage functionality.

---

## Preparation

Represents opponent-specific preparation.

A preparation will eventually combine information about:

```text
opponent repertoire
+
user repertoire
+
recent opponent games
+
historical opponent games
+
position statistics
```

A preparation may include:

* expected openings;
* move frequencies;
* recent repertoire changes;
* relevant games;
* positions to review;
* personal notes;
* engine analysis.

---

# Opponent Preparation

The key long-term product interaction is:

```text
I am playing <opponent>
with <White/Black>
```

The application should help answer:

```text
What are they likely to play?
What have they played recently?
What positions are likely to occur?
What parts of my repertoire overlap with theirs?
What should I prepare?
```

Recent games should be distinguishable from lifetime/historical games.

For example:

```text
Opponent as White

All games:
1.e4  62%
1.d4  28%

Last 12 months:
1.e4  83%
1.d4  12%
```

This can expose repertoire changes that lifetime statistics hide.

---

# Opening and Position Searching

Opponent preparation should eventually be position-aware.

A future search should support questions such as:

```text
Given this chess position,
how frequently has this opponent reached it,
and what did they play next?
```

Do not assume raw PGN text searching is sufficient.

Possible derived indexing mechanisms include:

* move sequences;
* FEN-derived data;
* position hashes;
* opening classification;
* ECO codes.

The exact indexing strategy should be chosen only after the initial game workflow exists and realistic query requirements are available.

---

# Reference Games

Reference games are chess games available for opponent preparation that are not necessarily part of the user's personal database.

Possible sources include:

```text
internal games
uploaded PGNs
Lichess
Chess.com
Mega Database
other tournament databases
```

The core preparation system must not depend directly on any one provider.

Conceptually:

```text
Opponent Preparation
        ↓
ReferenceGameProvider
        ↓
 ┌──────┼─────────┬──────────┐
 │      │         │          │
Internal Mega   Lichess    Other
```

The actual source of reference games should be replaceable.

---

# Mega Database

Mega Database is a possible future reference-game source.

The project should not initially import millions of Mega Database games into the primary application database.

Possible future approaches include:

1. querying a supported/licensed remote source;
2. maintaining a separate local searchable reference-game database;
3. periodically importing permitted data into a dedicated reference-game service.

Licensing and redistribution rights must be understood before making Mega Database data accessible to multiple club users.

The initial application should work without Mega Database.

---

# Architecture

## Initial architecture

Start as a modular monolith.

```text
                 Web Application
                       │
                       ▼
                  Chess Core
                       │
                       ▼
                  PostgreSQL
```

The core contains modules for:

```text
games
players
game imports
preparation
users
clubs
```

Modules should have clear boundaries even while deployed together.

---

# Expected Evolution

Potential future architecture:

```text
                       Web
                        │
                        ▼
                  ┌───────────┐
                  │   Core    │
                  └─────┬─────┘
                        │
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
     PostgreSQL    Notation       Reference
                   Recognition     Game Search
                    Service         Service
                        │
                        ▼
                    AI / ML
```

A future analysis worker/service may run Stockfish.

This is a possible evolution, not the required starting architecture.

---

# Core Technology

## Backend

```text
Java 25
Spring Boot 4.x
Maven
chesslib
```

Chess move legality, SAN parsing, FEN handling and PGN reading use `chesslib`,
wrapped behind interfaces owned by the core service. See
`docs/adr/0001-java-chess-rules-library.md`.

The core backend owns:

* transactional business logic;
* games;
* players;
* game-import workflows;
* users;
* clubs;
* permissions;
* preparation metadata.

---

## Database

```text
PostgreSQL 18
```

Use relational modelling for strongly structured domain data.

Use `JSONB` selectively for flexible derived chess structures.

The initial system should not use MongoDB unless a concrete persistence requirement shows that PostgreSQL is becoming unsuitable.

---

## Frontend

```text
React
TypeScript
Vite
Yarn
```

Use feature-based organisation.

The game viewer/review experience should support:

* chessboard navigation;
* move list;
* scoresheet image;
* confidence/issue indicators;
* move correction.

---

## Scoresheet storage

Scoresheet images should be stored in object storage rather than directly in the relational database.

Local development may use an S3-compatible local service.

Production may use AWS S3.

---

## Notation recognition

The eventual dedicated notation-recognition service is expected to use:

```text
Python
FastAPI
```

Python is isolated to the area where its AI/ML ecosystem provides a clear benefit.

The core application should communicate with recognition through a stable internal contract.

---

# Service Boundaries

## Core

Owns:

```text
users
players
clubs
games
game imports
permissions
preparation metadata
canonical PGN
```

## Notation Recognition

Owns:

```text
image interpretation
recognition candidates
confidence
recognition metadata
```

Does not own canonical chess games.

## Reference Game Search

Future responsibility:

```text
large external/reference datasets
provider integrations
game lookup
position search
search optimisation
```

## Analysis

Possible future responsibility:

```text
Stockfish
batch game analysis
repertoire analysis
CPU-intensive work
```

---

# Initial Product Scope

The first milestone is intentionally small.

## Milestone 1 — Game database

Implement:

```text
Player
Game
PGN validation
PGN import
Game persistence
Game list
Game viewer
```

A user should be able to paste/import a valid PGN, save it, and replay the game.

---

## Milestone 2 — GameImport workflow

Implement:

```text
GameImport
scoresheet image upload
processing status
review screen
manual move correction
confirmation
Game creation
```

Use fake recognition results initially.

This allows the entire workflow to be built before introducing AI uncertainty.

---

## Milestone 3 — AI recognition

Replace the fake recognizer with an actual multimodal recognition implementation.

The AI integration should remain provider-independent from the perspective of the domain.

Measure:

* per-move accuracy;
* games requiring correction;
* number of corrections per game;
* recognition failures.

---

## Milestone 4 — Personal opponent preparation

Use games already stored in the application.

Implement:

```text
player search
games by player
colour filtering
recent game filtering
opening statistics
basic move-tree statistics
```

Do not add Mega Database yet.

---

## Milestone 5 — Reference games

Introduce the reference-game abstraction.

Integrate one useful external source.

Validate the preparation architecture before optimising for extremely large databases.

---

## Milestone 6 — Club functionality

Introduce:

```text
Club
ClubMembership
club permissions
shared club game access
```

Obs Chess Club is the initial intended club use case.

---

# Non-Goals for V1

Do not initially build:

* a complete Mega Database clone;
* a custom handwriting ML model;
* Kubernetes infrastructure;
* many independently deployed microservices;
* a full ChessBase replacement;
* real-time multiplayer chess;
* tournament pairing software;
* a chess engine;
* advanced repertoire generation.

Stockfish or other existing engines should be used for analysis when engine functionality is introduced.

---

# Key Architectural Principles

## Confirmed data beats inferred data

A player-confirmed PGN is canonical.

AI results are proposals.

---

## Human review is expected

Recognition does not need to be perfect to be valuable.

The product should make correcting uncertain recognition extremely fast.

---

## Chess rules are deterministic

Use chess software to validate chess.

Do not ask an LLM to replace deterministic move legality.

---

## Services need reasons to exist

Create a deployable microservice because it has:

* different technology;
* different scaling;
* different compute;
* different deployment requirements;
* strong ownership boundaries;

not merely because it represents another feature.

---

## External sources are providers

The application should not become structurally dependent on Mega Database, Lichess, Chess.com or any other reference provider.

---

## Build vertical slices

Prefer completing:

```text
input
→ domain
→ persistence
→ API
→ UI
```

for one useful feature before expanding horizontally across future architecture.

---

# Current First Task

Create the project skeleton and implement the first vertical slice:

```text
create Player
      ↓
import/paste PGN
      ↓
validate PGN
      ↓
create Game
      ↓
persist Game
      ↓
list Games
      ↓
view/replay Game
```

Scoresheet recognition should follow after this works.
