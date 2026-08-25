package com.chessapp.game.api;

import com.chessapp.game.application.ImportPgn;
import com.chessapp.game.application.PgnImportResult;
import com.chessapp.game.domain.Game;
import jakarta.validation.Valid;
import java.net.URI;
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

    private final ImportPgn importPgn;

    public GameController(ImportPgn importPgn) {
        this.importPgn = importPgn;
    }

    @PostMapping
    public ResponseEntity<Object> importGame(@Valid @RequestBody ImportPgnRequest request) {
        return switch (importPgn.execute(request.pgn())) {
            case PgnImportResult.Imported imported -> created(imported.game());
            case PgnImportResult.Rejected ignored ->
                    throw new UnsupportedOperationException("rejection mapping: task 3");
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
}
