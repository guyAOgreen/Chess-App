package com.chessapp.game.domain;

import java.util.List;

/**
 * One page of a game search, plus the size of the whole filtered set.
 *
 * <p>{@code totalElements} counts every game matching the filters rather than the
 * rows returned, so a caller can render a pager. It is the only aggregate here:
 * total pages is presentation arithmetic and belongs to the response type.
 *
 * <p>The rows and the total come from two statements. Under PostgreSQL's default
 * {@code READ COMMITTED} isolation each takes its own snapshot, so a concurrent
 * write can leave them momentarily disagreeing. That is accepted — see the design's
 * known limitations — and no read-only transaction closes it.
 */
public record GamePage(List<GameSummary> content, int page, int size, long totalElements) {

    public GamePage {
        content = List.copyOf(GameValues.required(content, "content"));
    }
}
