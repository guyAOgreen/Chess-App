package com.chessapp.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GamePageTest {

    private static GameSummary summary() {
        return new GameSummary(UUID.randomUUID(),
                new GameSide(UUID.randomUUID(), "Green, Guy", 1850),
                new GameSide(UUID.randomUUID(), "Club Opponent", null),
                "Club Championship", "London ENG", "3",
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "C60", GameSource.PGN_IMPORT);
    }

    @Test
    void carriesTheFilteredTotalAlongsideTheRowsOfOnePage() {
        GamePage page = new GamePage(List.of(summary()), 2, 25, 143);

        assertThat(page.content()).hasSize(1);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(25);
        assertThat(page.totalElements()).isEqualTo(143);
    }

    /**
     * The caller's list is copied, not adopted. A page handed to the API layer is a
     * value, and a mutation of the list it was built from must not change it.
     */
    @Test
    void copiesTheContentItIsGiven() {
        List<GameSummary> mutable = new ArrayList<>(List.of(summary()));

        GamePage page = new GamePage(mutable, 0, 25, 1);
        mutable.clear();

        assertThat(page.content()).hasSize(1);
    }

    @Test
    void rejectsNullContentRatherThanCarryingIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GamePage(null, 0, 25, 0))
                .withMessageContaining("content");
    }

    @Test
    void isEmptyRatherThanNullWhenNothingMatched() {
        GamePage page = new GamePage(List.of(), 0, 25, 0);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void rejectsASummaryWithNoIdentity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameSummary(null,
                        new GameSide(UUID.randomUUID(), "Green, Guy", null),
                        new GameSide(UUID.randomUUID(), "Club Opponent", null),
                        null, null, null, null, GameResult.DRAW, null, GameSource.PERSONAL))
                .withMessageContaining("id");
    }

    @Test
    void refusesToBeModifiedThroughTheListItExposes() {
        GamePage page = new GamePage(List.of(summary()), 0, 25, 1);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> page.content().clear());
    }
}
