package com.chessapp.game.api;

import com.chessapp.game.domain.GamePage;
import java.util.List;

/**
 * The list envelope.
 *
 * <p>Written out rather than serialising Spring Data's {@code PageImpl}, whose JSON
 * shape Spring Data itself warns is not stable across versions. Nothing here holds
 * one in any case — the query is hand-built Criteria — so this is a note for anyone
 * tempted to reintroduce it.
 *
 * <p>{@code totalPages} is derived here rather than in the domain {@link GamePage},
 * which stays the minimum a search produces. Total pages is presentation
 * arithmetic: it exists so a client can draw a pager.
 */
public record GamePageResponse(List<GameSummaryResponse> content,
                               int page,
                               int size,
                               long totalElements,
                               int totalPages) {

    public static GamePageResponse from(GamePage page) {
        return new GamePageResponse(
                page.content().stream().map(GameSummaryResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                totalPages(page.totalElements(), page.size()));
    }

    /**
     * Ceiling division in integer arithmetic. Floating point would round wrongly for
     * a large total, and an empty filtered set is zero pages rather than one — there
     * is no page to draw.
     */
    private static int totalPages(long totalElements, int size) {
        return (int) ((totalElements + size - 1) / size);
    }
}
