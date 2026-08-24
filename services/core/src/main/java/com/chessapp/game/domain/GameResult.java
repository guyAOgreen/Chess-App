package com.chessapp.game.domain;

/**
 * How a game finished. Authoritative: canonical PGN assembly derives both the
 * {@code Result} tag and the terminal movetext token from this value, rather than
 * from anything stored alongside the moves.
 */
public enum GameResult {

    WHITE_WON("1-0"),
    BLACK_WON("0-1"),
    DRAW("1/2-1/2"),
    /** Adjourned, abandoned, or a result that was never recorded. */
    UNFINISHED("*");

    private final String pgnToken;

    GameResult(String pgnToken) {
        this.pgnToken = pgnToken;
    }

    /** The PGN terminal token for this result, as it appears in a game score. */
    public String pgnToken() {
        return pgnToken;
    }
}
