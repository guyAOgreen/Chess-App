package com.chessapp.gameimport.domain;

import java.util.Arrays;

/**
 * Where a {@link GameImport} is in its lifecycle.
 *
 * <pre>
 * UPLOADED → PROCESSING → READY_FOR_REVIEW → CONFIRMED
 *                 └──────→ FAILED
 * </pre>
 *
 * <p>{@link #canTransitionTo} is the only place the allowed transitions are
 * written down. Callers attempt a transition and let it fail; they do not
 * compare statuses themselves.
 *
 * <p>{@code READY_FOR_REVIEW} means processing produced something reviewable,
 * not that the moves are legal, complete or unambiguous — those issues are
 * reviewed, they do not fail the import. {@code FAILED} means no reviewable
 * result could be produced, and is terminal: the user starts a new import.
 */
public enum GameImportStatus {
    UPLOADED,
    PROCESSING,
    READY_FOR_REVIEW,
    CONFIRMED,
    FAILED;

    /**
     * Whether an import in this status may move to {@code next}. Staying in the
     * same status is never allowed, so a duplicate command is visible rather
     * than silently ignored.
     *
     * <p>The switch has no {@code default} on purpose: a new status does not
     * compile until its transitions have been decided.
     */
    public boolean canTransitionTo(GameImportStatus next) {
        if (next == null) {
            throw new IllegalArgumentException("next status is required");
        }
        return switch (this) {
            case UPLOADED -> next == PROCESSING;
            case PROCESSING -> next == READY_FOR_REVIEW || next == FAILED;
            case READY_FOR_REVIEW -> next == CONFIRMED;
            case CONFIRMED, FAILED -> false;
        };
    }

    /** No transition leaves this status. Derived from the table so the two cannot drift. */
    public boolean isTerminal() {
        return Arrays.stream(values()).noneMatch(this::canTransitionTo);
    }
}
