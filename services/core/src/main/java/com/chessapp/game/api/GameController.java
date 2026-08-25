package com.chessapp.game.api;

import com.chessapp.chess.PgnError;
import com.chessapp.game.application.ImportPgn;
import com.chessapp.game.application.PgnImportResult;
import com.chessapp.game.domain.Game;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The path is written out rather than applied by a {@code WebMvcConfigurer} prefix:
 * the vite dev server proxies {@code /api} without rewriting, so the backend must
 * serve {@code /api/games}, and a reader searching the repository for that path
 * should find it.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    /** RFC 9457 type for a document that cannot become a game. Relative by design. */
    private static final URI INVALID_PGN = URI.create("/errors/invalid-pgn");

    private final ImportPgn importPgn;

    public GameController(ImportPgn importPgn) {
        this.importPgn = importPgn;
    }

    @PostMapping
    public ResponseEntity<Object> importGame(@Valid @RequestBody ImportPgnRequest request) {
        return switch (importPgn.execute(request.pgn())) {
            case PgnImportResult.Imported imported -> created(imported.game());
            case PgnImportResult.Rejected rejected -> invalidPgn(rejected.error());
        };
    }

    /**
     * A relative URI built from the created identifier rather than from
     * {@code ServletUriComponentsBuilder}. There is no proxy configuration that
     * would make an absolute URI correct, and a relative one cannot be wrong.
     */
    private static ResponseEntity<Object> created(Game game) {
        return ResponseEntity.created(URI.create("/api/games/" + game.id()))
                .body(GameResponse.from(game));
    }

    /**
     * Every PgnErrorCode is a 422: the request was understood and the content was
     * the problem. The code says which, so splitting the status would give clients
     * two things to branch on instead of one.
     *
     * <p>The content type is set explicitly rather than relying on Spring to infer
     * it from the body type, so the wire format is stated where it is decided.
     *
     * <p>ply is set only when present, so a client can branch on the field's
     * presence rather than on a null.
     */
    private static ResponseEntity<Object> invalidPgn(PgnError error) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        problem.setType(INVALID_PGN);
        problem.setTitle("Invalid PGN");
        problem.setDetail(error.message());
        problem.setProperty("code", error.code().name());
        if (error.ply() != null) {
            problem.setProperty("ply", error.ply());
        }
        return ResponseEntity.unprocessableContent()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
