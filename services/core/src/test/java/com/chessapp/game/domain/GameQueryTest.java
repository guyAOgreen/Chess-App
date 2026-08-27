package com.chessapp.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameQueryTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private static GameQuery query(UUID playerId, GameColour colour,
                                   LocalDate from, LocalDate to, String event) {
        return new GameQuery(playerId, colour, null, from, to, event,
                GameSort.PLAYED_ON, SortDirection.DESC, 0, 25);
    }

    @Test
    void acceptsAQueryWithNoFiltersAtAll() {
        GameQuery query = query(null, null, null, null, null);

        assertThat(query.playerId()).isNull();
        assertThat(query.event()).isNull();
    }

    @Test
    void rejectsAColourWithNoPlayerToConstrain() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> query(null, GameColour.WHITE, null, null, null))
                .withMessageContaining("playerId");
    }

    @Test
    void acceptsAColourAlongsideAPlayer() {
        assertThat(query(PLAYER, GameColour.BLACK, null, null, null).colour())
                .isEqualTo(GameColour.BLACK);
    }

    @Test
    void rejectsAFromAfterItsTo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> query(null, null,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null))
                .withMessageContaining("from must not be after to");
    }

    /** The bounds are inclusive, so a single-day range is a legitimate request. */
    @Test
    void acceptsAFromEqualToItsTo() {
        LocalDate day = LocalDate.of(2026, 3, 14);

        assertThat(query(null, null, day, day, null).from()).isEqualTo(day);
    }

    @Test
    void trimsSurroundingWhitespaceFromTheEventFilter() {
        assertThat(query(null, null, null, null, "  championship  ").event())
                .isEqualTo("championship");
    }

    @Test
    void treatsABlankEventFilterAsNoFilter() {
        assertThat(query(null, null, null, null, "   ").event()).isNull();
    }

    /**
     * GameValues.optionalTag maps "?" to null because it is the PGN unknown marker.
     * A search term is not a tag value: someone looking for an event containing a
     * question mark must not silently get every game.
     */
    @Test
    void keepsAQuestionMarkAsASearchTermRatherThanTreatingItAsUnknown() {
        assertThat(query(null, null, null, null, "?").event()).isEqualTo("?");
    }

    @Test
    void rejectsANullSortRatherThanDefaultingIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameQuery(null, null, null, null, null, null,
                        null, SortDirection.DESC, 0, 25))
                .withMessageContaining("sort");
    }

    @Test
    void rejectsANullDirectionRatherThanDefaultingIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameQuery(null, null, null, null, null, null,
                        GameSort.PLAYED_ON, null, 0, 25))
                .withMessageContaining("direction");
    }

    @Test
    void rejectsANegativePage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameQuery(null, null, null, null, null, null,
                        GameSort.PLAYED_ON, SortDirection.DESC, -1, 25))
                .withMessageContaining("page");
    }

    @Test
    void rejectsAPageOfNoRows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GameQuery(null, null, null, null, null, null,
                        GameSort.PLAYED_ON, SortDirection.DESC, 0, 0))
                .withMessageContaining("size");
    }
}
