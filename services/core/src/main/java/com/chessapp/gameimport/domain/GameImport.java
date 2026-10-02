package com.chessapp.gameimport.domain;

import java.util.UUID;

/**
 * An attempt to create a game from a source that needs processing and review,
 * such as a scoresheet photo. Deliberately separate from {@code Game}: an import
 * may be incomplete, ambiguous or wrong, a {@code Game} is confirmed.
 *
 * <p>A {@code GameImport} always exists in the database, so {@code id} is never
 * null. Construction restores a snapshot rather than replaying a history, so the
 * same invariants apply whether the value is new or rehydrated from a row.
 *
 * <p>{@code failureReason} is present exactly when the status is
 * {@link GameImportStatus#FAILED}. It is an application-controlled explanation,
 * never a raw provider response or exception message.
 */
public record GameImport(UUID id, GameImportStatus status, String failureReason) {

    public GameImport {
        if (id == null) {
            throw new IllegalArgumentException("id is required; a GameImport is always persisted");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        failureReason = failureReason(status, failureReason);
    }

    /** {@code UPLOADED → PROCESSING}. */
    public GameImport startProcessing() {
        return transitionTo(GameImportStatus.PROCESSING, null);
    }

    /** {@code PROCESSING → READY_FOR_REVIEW}: a reviewable result exists, not necessarily a legal one. */
    public GameImport markReadyForReview() {
        return transitionTo(GameImportStatus.READY_FOR_REVIEW, null);
    }

    /** {@code PROCESSING → FAILED}: no reviewable result could be produced. */
    public GameImport fail(String reason) {
        return transitionTo(GameImportStatus.FAILED, reason);
    }

    /**
     * {@code READY_FOR_REVIEW → CONFIRMED}. This only changes the import's
     * status; creating the {@code Game} from the reviewed moves is the
     * confirmation use case's job (#18), in the same transaction.
     */
    public GameImport confirm() {
        return transitionTo(GameImportStatus.CONFIRMED, null);
    }

    /**
     * Checks the transition before building the next snapshot, so an illegal
     * transition is reported even when the accompanying reason is also invalid.
     * The returned value is a proposed next snapshot; it persists nothing.
     */
    private GameImport transitionTo(GameImportStatus next, String reason) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidGameImportTransition(status, next);
        }
        return new GameImport(id, next, reason);
    }

    /**
     * A non-failed import with any reason, even a blank one, is rejected rather
     * than normalised: it signals a stale or corrupt value, not an absent one.
     */
    private static String failureReason(GameImportStatus status, String raw) {
        if (status != GameImportStatus.FAILED) {
            if (raw != null) {
                throw new IllegalArgumentException(
                        "failureReason must be null unless status is FAILED, status was: " + status);
            }
            return null;
        }
        if (raw == null) {
            throw new IllegalArgumentException("failureReason is required when status is FAILED");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("failureReason must not be blank");
        }
        return trimmed;
    }
}
