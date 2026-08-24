package com.chessapp.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameTest {

    private static final UUID ID = UUID.fromString("019535d9-5b22-7f04-8e15-3c9a7d2f6b81");
    private static final GameSide WHITE =
            new GameSide(UUID.fromString("019535d9-3df7-79fb-b466-fa907fa17f9e"), "Green, Guy", 1850);
    private static final GameSide BLACK =
            new GameSide(UUID.fromString("019535d9-4aa1-7c2e-9d31-2b6f1c4e8a70"), "Club Opponent", null);

    private static Game game(UUID id, String movetext) {
        return new Game(id, WHITE, BLACK, "Club Championship", "London ENG", "3",
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "C60", GameSource.PGN_IMPORT,
                movetext, null);
    }

    @Test
    void holdsTheDatabaseAssignedIdentity() {
        assertThat(game(ID, "1. e4 e5").id()).isEqualTo(ID);
    }

    @Test
    void rejectsAMissingIdBecauseAGameIsAlwaysPersisted() {
        assertThatThrownBy(() -> game(null, "1. e4 e5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void revalidatesOnRehydrationSoACorruptRowCannotEnterTheDomain() {
        assertThatThrownBy(() -> game(ID, "1. e4 e5 1-0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result token");
    }
}
