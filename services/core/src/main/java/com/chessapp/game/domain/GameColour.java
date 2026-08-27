package com.chessapp.game.domain;

/**
 * Which colour a player had. Distinct from {@link GameSide}, which is one colour's
 * share of a particular game — this is the colour itself, used to narrow a search
 * to the games a player had White in, or Black.
 */
public enum GameColour {
    WHITE,
    BLACK
}
