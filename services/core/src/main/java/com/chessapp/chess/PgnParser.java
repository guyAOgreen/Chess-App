package com.chessapp.chess;

/**
 * Reads a PGN document into validated chess facts.
 *
 * <p>An interface with one implementation, because ADR 0001 names
 * wolfraam/chess-game as the fallback if chesslib is abandoned, and a seam is what
 * makes that a real option rather than a sentence in a document.
 */
public interface PgnParser {

    /** Never throws for bad input: an unusable document comes back as a rejection. */
    PgnParseResult parse(String pgn);
}
