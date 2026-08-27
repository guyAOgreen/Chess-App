package com.chessapp.game.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.game.domain.GameColour;
import com.chessapp.game.domain.GameQuery;
import com.chessapp.game.domain.GameSort;
import com.chessapp.game.domain.SortDirection;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs a real {@link Validator} rather than calling the {@code @AssertTrue} methods
 * directly, so the test proves the annotations are wired rather than that the
 * predicates are correct in isolation.
 */
class GameListParamsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    private static GameListParams params(UUID playerId, GameColour colour,
                                         LocalDate from, LocalDate to, String event,
                                         Integer page, Integer size) {
        return new GameListParams(playerId, colour, null, from, to, event,
                null, null, page, size);
    }

    private static Set<String> messages(GameListParams params) {
        return validator.validate(params).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void appliesTheDocumentedDefaultsWhenNothingWasSupplied() {
        GameListParams params = params(null, null, null, null, null, null, null);

        assertThat(params.sort()).isEqualTo(GameSort.PLAYED_ON);
        assertThat(params.direction()).isEqualTo(SortDirection.DESC);
        assertThat(params.page()).isZero();
        assertThat(params.size()).isEqualTo(25);
    }

    @Test
    void keepsSuppliedPagingAndSortingOverTheDefaults() {
        GameListParams params = new GameListParams(null, null, null, null, null, null,
                GameSort.PLAYED_ON, SortDirection.ASC, 3, 10);

        assertThat(params.direction()).isEqualTo(SortDirection.ASC);
        assertThat(params.page()).isEqualTo(3);
        assertThat(params.size()).isEqualTo(10);
    }

    @Test
    void trimsSurroundingWhitespaceFromTheEventFilter() {
        assertThat(params(null, null, null, null, "  championship ", null, null).event())
                .isEqualTo("championship");
    }

    /**
     * A blank filter reaching the query would become the pattern that matches every
     * game with an event and drops every game without one — a filter the user
     * believes they cleared, quietly removing rows.
     */
    @Test
    void treatsABlankEventFilterAsNoFilter() {
        assertThat(params(null, null, null, null, "   ", null, null).event()).isNull();
    }

    @Test
    void acceptsAFullyPopulatedRequest() {
        UUID player = UUID.randomUUID();

        assertThat(messages(params(player, GameColour.WHITE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "open", 0, 25)))
                .isEmpty();
    }

    @Test
    void rejectsAColourWithNoPlayerToNarrow() {
        assertThat(messages(params(null, GameColour.WHITE, null, null, null, null, null)))
                .contains("colour requires playerId");
    }

    @Test
    void rejectsAFromAfterItsTo() {
        assertThat(messages(params(null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null, null, null)))
                .contains("from must not be after to");
    }

    @Test
    void rejectsAPageSizeBeyondTheCap() {
        assertThat(messages(params(null, null, null, null, null, 0, 500))).isNotEmpty();
    }

    @Test
    void rejectsAPageOfNoRows() {
        assertThat(messages(params(null, null, null, null, null, 0, 0))).isNotEmpty();
    }

    @Test
    void rejectsANegativePage() {
        assertThat(messages(params(null, null, null, null, null, -1, 25))).isNotEmpty();
    }

    /**
     * setFirstResult takes an int, so an unbounded page overflows page * size to a
     * negative and becomes a 500 rather than a rejected request.
     */
    @Test
    void rejectsAPageBeyondTheOverflowBound() {
        assertThat(messages(params(null, null, null, null, null, 100_001, 25))).isNotEmpty();
    }

    @Test
    void rejectsAnEventTermBeyondTheLengthCap() {
        assertThat(messages(params(null, null, null, null, "x".repeat(256), null, null)))
                .isNotEmpty();
    }

    @Test
    void convertsToADomainQueryCarryingEveryValue() {
        UUID player = UUID.randomUUID();

        GameQuery query = new GameListParams(player, GameColour.BLACK, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), " open ",
                GameSort.PLAYED_ON, SortDirection.ASC, 2, 10).toQuery();

        assertThat(query.playerId()).isEqualTo(player);
        assertThat(query.colour()).isEqualTo(GameColour.BLACK);
        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(query.to()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(query.event()).isEqualTo("open");
        assertThat(query.direction()).isEqualTo(SortDirection.ASC);
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(10);
    }
}
