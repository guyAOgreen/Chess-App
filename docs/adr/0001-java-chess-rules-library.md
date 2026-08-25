# 1. Java chess rules library

Date: 2026-08-19

Status: Accepted

Issue: [#2](https://github.com/guyAOgreen/Chess-App/issues/2)

Chess terminology used here is defined in the [glossary](../glossary.md).

## Context

Chess legality and PGN construction must be deterministic. The core service needs
legal move generation, SAN parsing, board reconstruction, FEN handling and PGN
validation for three workflows:

- PGN import (`POST /games`), where the input is arbitrary third-party text;
- scoresheet recognition, where the input is a model's guess and is expected to be
  wrong, ambiguous or truncated;
- opponent preparation, which needs position identity to group transpositions.

Recognition output is evidence, not truth. The library is what turns evidence into
a verified game, so its failure modes matter as much as its features.

## Options considered

| Option | Licence | Latest release | Activity | Notes |
| --- | --- | --- | --- | --- |
| **bhlangonijr/chesslib** | Apache-2.0 | 1.3.7 (2026-06-30) | commits to 2026-07 | 292 stars, 71 classes, 145 KB, Java 11 bytecode |
| wolfraam/chess-game | MIT | 2.3 (2024-10) | last push 2024-10 | 12 stars; SAN/FAN/LAN/UCI/PGN, opening detection |
| Chesspresso / ictk | LGPL / various | — | abandoned | No current release; LGPL is a poorer fit than Apache-2.0 |
| Write our own move generator | — | — | — | Weeks of work to reach correctness others already have |

Only chesslib is both currently maintained and broad enough to cover move
generation, SAN, FEN and PGN in one dependency. wolfraam/chess-game is credible and
permissively licensed but has been dormant since October 2024 and has a much smaller
user base; it is the fallback if chesslib is abandoned.

## Decision

Use **`com.github.bhlangonijr:chesslib:1.3.7`**, wrapped behind interfaces owned by
the core application. No code outside the chess module may import
`com.github.bhlangonijr.*`.

The wrapper exists because the library's contract is weaker than its API suggests
(see Verified behaviour below) and because the constraints listed there must be
enforced in exactly one place rather than at every call site. It also keeps the
fallback option real.

## Verification

The evaluation was a throwaway probe run against the real library, not a reading of
its README. The probe has been deleted; its findings are recorded here.

### What works

- **Move generation is correct.** Perft matched published node counts exactly:
  20 / 400 / 8,902 / 197,281 from the start position, and 48 / 2,039 / 97,862 on
  Kiwipete, which exercises castling, en passant, promotion and pins.
- **Draw and terminal detection** — checkmate, stalemate, threefold repetition,
  fifty-move rule and insufficient material all reported correctly.
- **SAN parsing is appropriately tolerant on input and normalising on output.**
  `Re1`, `Re1+` and `Re1#` are all accepted for the same move and emitted as `Re1+`;
  a spurious `+` on a quiet move is accepted and stripped; `a8Q` is accepted and
  emitted as `a8=Q+`. File, rank and full-square disambiguation (`Nbd2`, `R3a2`,
  `Qh1e4`) all parse, and output is emitted with the minimum disambiguation
  required. Movetext may start on a black move (`1... e5`). This tolerance matters
  for handwritten scoresheets, where check marks are routinely missing or wrong.
- **Castling rights and en passant** are tracked correctly, including forfeiture
  after a rook or king move.
- **FEN round-trips exactly**, including halfmove and fullmove counters.
- **PGN reading handles real-world files** — Seven Tag Roster plus `WhiteElo`,
  `BlackElo`, `ECO` and `Termination`; NAGs, brace comments, `[%clk]` annotations,
  nested variations and multi-game files. `PgnIterator` accepts an
  `Iterable<String>`, so a request body can be parsed without writing a temp file.
  **Corrected 2026-08-24:** "handles" means structure, not legality. The reader
  accepts an illegal pawn move; see constraint 1.
- **Zobrist hashing transposes correctly** — 1.e4 e5 2.Nf3 and 1.Nf3 e5 2.e4 produce
  the same key. This is the basis for position indexing in opponent preparation.

### Constraints the wrapper must enforce

These are the reasons the wrapper is not optional.

1. **`doMove` and `isMoveLegal` do not enforce chess legality.** This is documented
   library behaviour, not a bug, and it is severe. `board.doMove("e5")` from the
   start position returns `true` and moves the e2 pawn three squares to e5.
   `board.isMoveLegal(e1-e4, fullValidation = true)` returns `true` for a king
   moving three squares. Both check only that the resulting position is internally
   valid. They do correctly reject moves that leave one's own king in check.

   **Corrected 2026-08-24 by a second probe.** The original text named SAN parsed
   through `MoveList` as a second authoritative path. It is not.
   `MoveList.loadFromSan("1. e5 e5")` is accepted and yields `1. e5 exe5` — a white
   pawn moving three squares from e2 — and the PGN reader accepts the same input.
   Illegal piece moves and illegal castling *are* rejected; the hole is in pawn
   decoding specifically.

   Only `board.legalMoves()` is authoritative. The wrapper must replay every move
   against it and must never accept a move from any other source without that check.

2. **Failures are unchecked, and arrive from more than one place.**
   `MoveConversionException`, `PgnException` and `MoveException` all extend
   `RuntimeException`; the original text described the first as a declared checked
   exception, which it is not. **Corrected 2026-08-24:** SAN describing a capture of
   the king surfaces as `PgnException` thrown by the iterator itself, not as
   `ArrayIndexOutOfBoundsException` during move loading, so error handling must wrap
   the iteration. A game with no moves throws `NullPointerException` inside
   `loadMoveText()`. Recognition will produce exactly this kind of
   nonsense. The wrapper must catch `RuntimeException` and translate to a domain error.
   Nothing here needs declaring, and nothing may be left uncaught on the
   assumption that it would be.

3. **`PgnIterator` validates lazily.** Iterating yields a `Game` without having
   verified its movetext; the failure surfaces later, when the half-moves are read.
   Import code must force move loading inside its own error handling rather than
   treating a successfully returned `Game` as a valid one.

4. **Raw `MoveList.loadFromSan` is stricter than the PGN reader.** It rejects
   trailing result tokens (`1-0`, `1/2-1/2`), the `0-0` spelling of castling (only
   `O-O` with letter O is accepted), and the `e.p.` suffix. Movetext taken from
   anywhere other than the PGN reader must be normalised first.

5. **`Game.toPgn()` is not suitable for producing canonical PGN.** It injects tags
   that were not in the input (`PlyCount`, `TimeControl "-"`), reorders the tag pair
   section, and emits comments with misplaced spacing (`{solid }Bc5`). Canonical PGN
   should be assembled by us from validated tags plus
   `MoveList.toSanWithMoveNumbers()`, which produces clean movetext. How canonical
   PGN is stored is decided separately in the ADR for issue #3.

6. **`Board` is mutable and not thread-safe.** Construct one per operation; do not
   share or cache instances across requests.

## Distribution and licensing

- **Licence**: Apache-2.0. Permissive, compatible with this project, no copyleft
  obligation on our source.
- **Transitive dependencies**: one at compile scope, `org.apache.commons:commons-lang3`.
  JUnit 4 is test-scoped and does not reach our classpath.
- **Bytecode**: Java 11 (class file major version 55), so it runs on Java 25.
  Verifying the whole backend build on JDK 25 is issue #28 and is not settled here —
  the probe ran on JDK 21, which is the only JDK installed locally.
- **Distribution risk**: chesslib is published through **JitPack, not Maven Central**.
  This adds a build-time dependency on `jitpack.io` and artifacts that are built on
  demand from GitHub tags rather than signed and immutably hosted. Accepted, with
  these mitigations:
  - pin the exact version (never a `-SNAPSHOT` or branch reference);
  - the CI pipeline (issue #26) must cache the Maven repository so builds do not hit
    JitPack on every run;
  - if JitPack becomes unreliable, mirror the artifact into an internal repository —
    the jar is 145 KB with one transitive dependency, so this is cheap.

## Consequences

- Issue #6 (PGN parsing and validation service) adds the dependency and the JitPack
  repository to `services/core/pom.xml`, and builds the wrapper. This ADR
  deliberately leaves the build files unchanged.
- The wrapper lives in a `chess` module in the core service and owns move
  validation, SAN handling, FEN, position hashing and PGN reading. Its interface is
  expressed in our own types; chesslib types do not cross the boundary.
- Constraints 1–6 must each be covered by a test in our own test suite. They are
  assertions about behaviour we depend on, so a library upgrade that breaks one
  should fail our build rather than corrupt a game.
- Opponent preparation can index positions by Zobrist key from the start.
- Replacing chesslib later means reimplementing one module against unchanged
  interfaces. wolfraam/chess-game is the identified fallback.

## Not decided here

- How canonical PGN and derived move data are stored (issue #3).
- Opening classification and ECO assignment beyond reading the tag.
- Engine analysis, which is a separate concern from rules validation.
