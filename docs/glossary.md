# Glossary

Chess terminology used in this project's documentation and code. Written for a
developer who does not play chess; entries explain why a term matters here, not
only what it means.

---

## Notation and formats

**ECO** — Encyclopaedia of Chess Openings. A three-character code from `A00` to
`E99` classifying which opening a game used, such as `C65` for a line of the Ruy
Lopez. Carried as a PGN tag and stored as a column so games can be grouped by
opening.

**FEN** — Forsyth–Edwards Notation. A one-line encoding of a single position:
piece placement, side to move, castling rights, en passant target square, and the
two move counters. It describes a board without any history of how it was reached,
which makes it the usual way to set up a position for a test.

**movetext** — In PGN, the section after the tag pairs that holds the moves. Also a
column name in this project: `games.movetext` stores validated SAN with move
numbers, and deliberately excludes tag pairs and the result token.

**NAG** — Numeric Annotation Glyph. A move annotation written as `$` followed by a
number, such as `$6` for "questionable move". It is PGN's machine-readable
equivalent of the symbols a human writes, like `?!`.

**PGN** — Portable Game Notation. The standard plain-text format for recording
chess games: a block of tag pairs followed by the movetext. A single file may
contain many games.

**result token** — The symbol that ends the movetext. `1-0` White won, `0-1` Black
won, `1/2-1/2` draw, `*` unfinished or unknown.

**SAN** — Standard Algebraic Notation. The way moves are normally written for
people: a piece letter and destination square, such as `Nf3`, `exd5`, `O-O` or
`a8=Q+`. Contrast with coordinate notation like UCI's `g1f3`, which names both
squares and needs no knowledge of the position to read.

**Seven Tag Roster** — The seven tag pairs the PGN specification requires, in a
fixed order: `Event`, `Site`, `Date`, `Round`, `White`, `Black`, `Result`. Unknown
string values are written `?` and an unknown date `????.??.??`, so the tags are
always present even when the facts are not.

**tag pair** — One PGN header line, written `[Name "value"]` — for example
`[White "Green, Guy"]`. The tag pairs carry the metadata about a game; the movetext
carries the game itself.

**variation** — An alternative line of moves recorded alongside what was actually
played, written in parentheses in PGN. Used for analysis: "he played this, but
this other move was better".

---

## Rules and play

**castling** — A move where the king and a rook move simultaneously. Written `O-O`
kingside and `O-O-O` queenside, using the letter O. The digit-zero spelling `0-0`
appears in some files but is not accepted by our parser.

**check, checkmate, stalemate** — Check is a king under attack. Checkmate is check
with no legal move available, and ends the game decisively. Stalemate is having no
legal move while *not* in check, and is a draw. The distinction matters because a
result must be consistent with the final position.

**disambiguation** — Extra detail added to SAN when two identical pieces could
reach the same square, so the notation stays unambiguous. By file (`Nbd2`), by rank
(`R1a3`), or by both (`Qh4e1`) when file alone is not enough.

**en passant** — A pawn capture of an enemy pawn that has just advanced two squares,
taken as though it had advanced only one. It is legal only on the move immediately
after, which is why FEN records a target square for it.

**legal move** — A move the rules permit in the position, including the requirement
that it must not leave one's own king in check. Distinct from a move that is merely
well-formed or that produces a valid-looking board — a distinction ADR 0001 turns
on entirely.

**ply, half-move** — A single move by a single player. In ordinary chess usage a
"move" means a pair, White's and Black's, so a forty-move game is eighty plies.
Both terms mean the same thing.

**position** — The arrangement of pieces together with the side to move, castling
rights and en passant availability. Two games that played different move orders can
arrive at exactly the same position.

**promotion** — A pawn reaching the far rank must become another piece, written `=Q`
in SAN. Choosing anything other than a queen is called underpromotion, and is
occasionally the only winning move.

**threefold repetition, fifty-move rule, insufficient material** — The three ways a
game is drawn without either side being checkmated. *Threefold repetition*: the same
position occurs three times. *Fifty-move rule*: fifty moves by each side with no
capture and no pawn move. *Insufficient material*: neither side has enough pieces
left to deliver checkmate.

**transposition** — Reaching the same position by a different order of moves. It is
why opponent preparation compares positions rather than move sequences: two games
that look different on paper may be the same game by the tenth move.

---

## Engine and correctness

*These four are in reading order rather than alphabetical: each builds on the one
before it.*

**move generation** — Producing the complete list of legal moves in a position.
Everything else rests on it: if move generation is wrong, then validation, replay
and analysis are all wrong in ways that are hard to notice.

**perft** — Short for performance test. Count every distinct legal move sequence
from a position down to a given depth, and compare the total against published
values. It is the standard correctness check for a move generator, because a
generator that mishandles any single rule — castling rights, en passant timing,
pinned pieces — produces a different number. An exact match at depth four means
millions of sequences all agreed.

**Kiwipete** — A specific, widely used test position, deliberately crowded with
castling, en passant, promotions and pinned pieces. It is used alongside perft
because the ordinary starting position exercises too few of the awkward rules to
prove much.

**Zobrist hash** — A 64-bit key computed from a position such that identical
positions always produce identical keys, regardless of the moves that led there.
This makes it a cheap way to index positions and to spot transpositions.

---

## Product and domain

**Mega Database** — ChessBase's commercial database of several million historical
games. A possible source of reference games for opponent preparation, subject to
licensing.

**opening** — The first phase of a game, before the middlegame. Common sequences
have names and are classified by ECO code.

**opponent preparation** — Studying a specific opponent's previous games before
playing them, to work out what they are likely to play and to choose lines
accordingly. The long-term goal of this project.

**rating, Elo** — A number expressing a player's strength, around 1200 for a
beginner and above 2500 for a grandmaster. Elo is the rating system most federations
use; the number is recorded in PGN as `WhiteElo` and `BlackElo`.

**repertoire** — The set of openings a player habitually chooses. Preparation is
largely a question of how one player's repertoire meets another's, and repertoires
change over time — which is why recent games are worth distinguishing from
lifetime ones.

**scoresheet** — The paper form on which players write their moves by hand during a
tournament game, as required by the rules. Turning photographs of these into
validated games is a core product goal.
