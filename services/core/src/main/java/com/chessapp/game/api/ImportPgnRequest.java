package com.chessapp.game.api;

import jakarta.validation.constraints.Size;

/**
 * The submitted document, and nothing else. {@code source} is fixed server-side to
 * {@code PGN_IMPORT}, which is true by construction of anything arriving here; a
 * client-declared provenance would be a field to defend and nothing yet needs it.
 *
 * <p>An absent or null {@code pgn} is deliberately not rejected here. The parser
 * already answers "no PGN text was supplied" as {@code NOT_PGN}, and a
 * {@code @NotBlank} would add a second code path reaching the same conclusion in a
 * different format with a different status.
 *
 * <p>The cap is 1,048,576 UTF-16 code units, not bytes: Bean Validation measures
 * {@code String.length()}. It is an application-level limit that bounds the work
 * the parser can be asked to do — Jackson has already deserialised the body by the
 * time validation runs, so it does NOT cap bytes received. A transport limit needs
 * a reverse proxy or a servlet filter, and is tracked as a deployment
 * prerequisite.
 */
public record ImportPgnRequest(@Size(max = 1_048_576) String pgn) {
}
