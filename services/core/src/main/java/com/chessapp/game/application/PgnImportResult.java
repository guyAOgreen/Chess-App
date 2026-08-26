package com.chessapp.game.application;

import com.chessapp.chess.PgnError;
import com.chessapp.game.domain.Game;

/**
 * The outcome of importing a PGN document.
 *
 * <p>Sealed for the same reason {@code PgnParseResult} is: an invalid document is
 * an expected outcome for an endpoint fed by users, not an exceptional condition,
 * and a sealed type makes the failure impossible to forget where it is consumed.
 * That consumer is the controller, which is why the rejection travels this far as
 * a value rather than being re-wrapped as an exception at the layer boundary.
 */
public sealed interface PgnImportResult {

    record Imported(Game game) implements PgnImportResult {
    }

    record Rejected(PgnError error) implements PgnImportResult {
    }
}
