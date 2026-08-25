package com.chessapp.chess;

/** Why a PGN document was rejected. */
public enum PgnErrorCode {

    NOT_PGN,
    MULTIPLE_GAMES,
    NON_STANDARD_START_POSITION,
    NO_MOVES,
    UNREADABLE_MOVE,
    ILLEGAL_MOVE,
    PLAYER_UNKNOWN,
    RESULT_MISSING,
    RESULT_CONFLICT,
    RESULT_CONTRADICTS_POSITION
}
