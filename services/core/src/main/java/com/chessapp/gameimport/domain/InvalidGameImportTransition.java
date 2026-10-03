package com.chessapp.gameimport.domain;

/**
 * A {@link GameImport} was asked to move to a status its current status does not
 * allow — for example confirming an import twice, or a late processing result
 * arriving after the import has failed.
 *
 * <p>Unchecked, unlike the sealed results used for bad PGN: this is not ordinary
 * user input with a normal recovery path. It is a caller bug or a stale or
 * duplicate request, and the only sensible response is to reject it — at the HTTP
 * boundary, {@code 409 Conflict}.
 */
public class InvalidGameImportTransition extends RuntimeException {

    private final GameImportStatus from;
    private final GameImportStatus to;

    public InvalidGameImportTransition(GameImportStatus from, GameImportStatus to) {
        super("GameImport cannot move from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public GameImportStatus from() {
        return from;
    }

    public GameImportStatus to() {
        return to;
    }
}
