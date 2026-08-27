package com.chessapp.game.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.game.domain.GamePage;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.GameSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Total pages is the one piece of arithmetic in the API layer, and the case that
 * distinguishes a correct ceiling division from a naive one is a total that divides
 * the page size exactly. An unconditional {@code + 1} is wrong only there, so
 * without that case a regression to it passes every other assertion.
 */
class GamePageResponseTest {

    private static GameSummary summary() {
        return new GameSummary(UUID.randomUUID(),
                new GameSide(UUID.randomUUID(), "Green, Guy", 1850),
                new GameSide(UUID.randomUUID(), "Club Opponent", null),
                "Obs Club Championship", null, "3",
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "C60", GameSource.PGN_IMPORT);
    }

    private static int totalPages(long totalElements, int size) {
        return GamePageResponse.from(new GamePage(List.of(), 0, size, totalElements)).totalPages();
    }

    /** An empty filtered set is no pages at all; there is nothing to draw a pager for. */
    @Test
    void reportsNoPagesForAnEmptyFilteredSet() {
        assertThat(totalPages(0, 25)).isZero();
    }

    @Test
    void roundsAPartialPageUp() {
        assertThat(totalPages(1, 25)).isEqualTo(1);
        assertThat(totalPages(24, 25)).isEqualTo(1);
        assertThat(totalPages(26, 25)).isEqualTo(2);
    }

    /** The case that separates ceiling division from an unconditional increment. */
    @Test
    void doesNotAddAPageWhenTheTotalDividesExactly() {
        assertThat(totalPages(25, 25)).isEqualTo(1);
        assertThat(totalPages(50, 25)).isEqualTo(2);
        assertThat(totalPages(100, 100)).isEqualTo(1);
    }

    @Test
    void countsPagesForATotalSpanningMany() {
        assertThat(totalPages(143, 25)).isEqualTo(6);
        assertThat(totalPages(1_000, 100)).isEqualTo(10);
    }

    @Test
    void countsOnePagePerRowWhenThePageHoldsOne() {
        assertThat(totalPages(7, 1)).isEqualTo(7);
    }

    @Test
    void carriesThePageMetadataAndMapsEveryRow() {
        GamePageResponse response =
                GamePageResponse.from(new GamePage(List.of(summary(), summary()), 2, 25, 143));

        assertThat(response.content()).hasSize(2);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(25);
        assertThat(response.totalElements()).isEqualTo(143);
        assertThat(response.totalPages()).isEqualTo(6);
    }
}
