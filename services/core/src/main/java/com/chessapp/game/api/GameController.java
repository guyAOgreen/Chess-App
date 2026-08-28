package com.chessapp.game.api;

import com.chessapp.chess.PgnError;
import com.chessapp.game.application.ImportPgn;
import com.chessapp.game.application.PgnImportResult;
import com.chessapp.game.domain.Game;
import com.chessapp.game.domain.GameRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    private final GameRepository games;

    public GameController(ImportPgn importPgn, GameRepository games) {
        this.importPgn = importPgn;
        this.games = games;
    }

    @PostMapping
    public ResponseEntity<Object> importGame(@Valid @RequestBody ImportPgnRequest request) {
        return switch (importPgn.execute(request.pgn())) {
            case PgnImportResult.Imported imported -> created(imported.game());
            case PgnImportResult.Rejected rejected -> invalidPgn(rejected.error());
        };
    }

    /**
     * Calls the repository directly rather than through an application-layer class.
     * Binding the parameters and mapping the page are both DTO conversion, which is
     * this layer's job, and there is nothing left to orchestrate — a use case here
     * would be a single delegating line. {@code GameRepository} is declared in the
     * domain, so this is the API layer depending on a domain port rather than on
     * persistence; what it does skip is the application layer, and whether read
     * paths should have one on principle is #41.
     *
     * <p>{@code params} is bound as a model attribute by constructor binding.
     * {@code @Valid} makes a failed binding or a violated constraint a
     * {@code MethodArgumentNotValidException}, which Spring renders as 400
     * problem+json because {@code spring.mvc.problemdetails.enabled} is on.
     */
    @GetMapping
    public GamePageResponse listGames(@Valid GameListParams params) {
        return GamePageResponse.from(games.find(params.toQuery()));
    }

    /**
     * The full {@link GameResponse}, movetext included: the list row deliberately
     * omits the moves, so this is where a viewer gets them.
     *
     * <p>Reaches the repository directly for the reason {@link #listGames} gives.
     *
     * <p>A miss is a {@code ResponseStatusException} rather than a hand-built
     * ProblemDetail like {@link #invalidPgn}. Spring renders it as problem+json
     * because {@code spring.mvc.problemdetails.enabled} is on, so the shape matches
     * every other error on this API without inventing an error type a client would
     * have nothing to do with: a miss has one meaning, and the status carries it.
     *
     * <p>An {@code id} that cannot be parsed as a UUID fails in conversion and is a
     * 400, not a 404, matching the malformed {@code playerId} on the list endpoint.
     * The two say different things — the request was malformed, or the game is not
     * here — and a client can act on the difference.
     *
     * <p>That split is as clean as {@code UUID.fromString} is, and it is lenient:
     * it accepts non-canonical dash-separated forms and widens each group, so
     * {@code 1-1-1-1-1} parses as {@code 00000001-0001-0001-0001-000000000001} and
     * comes back 404 rather than 400. Left alone. Enforcing canonical form needs a
     * converter or a path regex, and the regex route is worse — a non-matching path
     * maps to no handler at all, which answers 404 for the plainly malformed
     * identifiers that are 400 today. A garbage identifier that happens to parse is
     * honestly reported as a game that is not here.
     */
    @GetMapping("/{id}")
    public GameResponse getGame(@PathVariable UUID id) {
        return games.findById(id)
                .map(GameResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No game with that identifier"));
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
