package com.chessapp.game.api;

import com.chessapp.game.domain.GameColour;
import com.chessapp.game.domain.GameQuery;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSort;
import com.chessapp.game.domain.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * The query parameters of {@code GET /api/games}, bound as one record so the two
 * cross-field rules have a single home and fail the same way the single-field ones
 * do.
 *
 * <p>Every enum-typed parameter is its own whitelist: an unknown {@code sort},
 * {@code direction}, {@code colour} or {@code result} fails in conversion, before a
 * query is built, and becomes a 400 through the problem-details handler.
 *
 * <p>Paging and sorting are boxed so an omitted parameter binds to null and this
 * constructor can apply the default. Defaults live here and nowhere else, so that
 * two places cannot come to disagree about what "no sort given" means —
 * {@link GameQuery} requires them and does not default them.
 */
public record GameListParams(UUID playerId,
                             GameColour colour,
                             GameResult result,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @Size(max = 255) String event,
                             GameSort sort,
                             SortDirection direction,
                             @Min(0) @Max(100_000) Integer page,
                             @Min(1) @Max(100) Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 25;

    public GameListParams {
        sort = sort == null ? GameSort.PLAYED_ON : sort;
        direction = direction == null ? SortDirection.DESC : direction;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        event = searchTerm(event);
    }

    /**
     * Blank and absent mean the same thing. A blank term reaching the query would
     * become the pattern matching every game that has an event, and — because a
     * null column never satisfies {@code LIKE} — silently dropping every game that
     * has none. A filter the user believes they cleared would quietly remove rows.
     */
    private static String searchTerm(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** A colour on its own has nothing to narrow, and a filter that silently does nothing
     * is worse than one that is refused. */
    @AssertTrue(message = "colour requires playerId")
    public boolean isColourAccompaniedByAPlayer() {
        return colour == null || playerId != null;
    }

    /** An unsatisfiable range is a client defect worth naming, not an empty list. */
    @AssertTrue(message = "from must not be after to")
    public boolean isDateRangeOrdered() {
        return from == null || to == null || !from.isAfter(to);
    }

    /**
     * Called only after validation has passed, so the boxed values are non-null and
     * the cross-field rules already hold. {@link GameQuery} checks them again
     * anyway — it is the invariant's owner, and this is its input gate.
     */
    public GameQuery toQuery() {
        return new GameQuery(playerId, colour, result, from, to, event,
                sort, direction, page, size);
    }
}
