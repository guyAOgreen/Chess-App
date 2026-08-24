package com.chessapp.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameSideTest {

    private static final UUID PLAYER_ID = UUID.fromString("019535d9-3df7-79fb-b466-fa907fa17f9e");

    @Test
    void storesTheTrimmedGameTimeName() {
        assertThat(new GameSide(PLAYER_ID, "  Green, Guy  ", 1850).name()).isEqualTo("Green, Guy");
    }

    @Test
    void rejectsAMissingPlayerId() {
        assertThatThrownBy(() -> new GameSide(null, "Green, Guy", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("playerId");
    }

    @Test
    void rejectsANullName() {
        assertThatThrownBy(() -> new GameSide(PLAYER_ID, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> new GameSide(PLAYER_ID, "   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsThePgnUnknownMarkerAsAName() {
        assertThatThrownBy(() -> new GameSide(PLAYER_ID, "?", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("?");
    }

    @Test
    void acceptsAnUnknownRating() {
        assertThat(new GameSide(PLAYER_ID, "Club Opponent", null).rating()).isNull();
    }

    @Test
    void rejectsANonPositiveRating() {
        assertThatThrownBy(() -> new GameSide(PLAYER_ID, "Green, Guy", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rating");
    }
}
