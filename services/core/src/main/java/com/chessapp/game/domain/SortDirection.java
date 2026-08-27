package com.chessapp.game.domain;

/**
 * Ascending or descending.
 *
 * <p>Deliberately not Spring Data's {@code Sort.Direction}: the domain stays free of
 * Spring. It also carries no null-handling of its own, because there is nothing to
 * choose — nulls sort last in both directions, and the query applies that.
 */
public enum SortDirection {
    ASC,
    DESC
}
