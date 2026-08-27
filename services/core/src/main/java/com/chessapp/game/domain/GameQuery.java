package com.chessapp.game.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A validated request for one page of games.
 *
 * <p>Every filter is optional, and null means "does not constrain". Paging and
 * ordering are not optional: defaults are applied once, at the HTTP boundary by
 * {@code GameListParams}, so that two places cannot come to disagree about what
 * "no sort given" means. A null {@code sort} here is a defect, not a request for
 * the default.
 *
 * <p>The two cross-field rules are enforced here as well as by bean validation at
 * the boundary. Bean validation is the input gate; a rejection reaching this
 * constructor means a defect in that gate rather than bad input, and the invariant
 * still holds for any future caller that is not the controller.
 *
 * <p>{@code size} is floored at 1 but not capped: a page of no rows is meaningless
 * whoever asks for it, while an upper bound is resource protection against an
 * untrusted caller and belongs at the boundary that has one.
 */
public record GameQuery(UUID playerId,
                        GameColour colour,
                        GameResult result,
                        LocalDate from,
                        LocalDate to,
                        String event,
                        GameSort sort,
                        SortDirection direction,
                        int page,
                        int size) {

    public GameQuery {
        if (colour != null && playerId == null) {
            throw new IllegalArgumentException(
                    "colour narrows a player filter, so it requires playerId");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "from must not be after to, was: " + from + " to " + to);
        }
        event = searchTerm(event);
        sort = GameValues.required(sort, "sort");
        direction = GameValues.required(direction, "direction");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative, was: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least one row, was: " + size);
        }
    }

    /**
     * Blank and absent mean the same thing, so both become null: an empty filter
     * that reached the query would become the pattern {@code %%}, which matches
     * every game that has an event and silently drops every game that has none.
     *
     * <p>Deliberately not {@link GameValues#optionalTag}. That method maps
     * {@code "?"} to null because it is the PGN marker for an unknown tag value. A
     * search term is not a tag value, and someone looking for an event containing a
     * question mark must not be handed every game instead.
     */
    private static String searchTerm(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
