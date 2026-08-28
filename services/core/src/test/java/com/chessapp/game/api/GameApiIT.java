package com.chessapp.game.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.game.domain.GameRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * The container is shared across the whole class with no cleanup between methods, so
 * a test that does not scope itself sees every game every other test ever created.
 * Each import test therefore names its own players, and each list test filters on
 * something only its own fixture matches — a unique event string, or the player id
 * the import reported.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GameApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameRepository games;

    /** A complete, valid PGN for the two named players. */
    private static String pgn(String white, String black) {
        return """
                [Event "Club Championship"]
                [Site "London ENG"]
                [Date "2026.03.14"]
                [Round "3.2"]
                [White "%s"]
                [Black "%s"]
                [Result "1-0"]
                [WhiteElo "1850"]
                [ECO "C60"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """.formatted(white, black);
    }

    /**
     * Built with Jackson rather than by hand: a PGN document is full of newlines
     * and quotation marks, and hand-escaping them into a JSON literal is a source
     * of test bugs that look like production bugs.
     */
    private ResultActions importing(String pgn) throws Exception {
        return mockMvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pgn", pgn))));
    }

    @Test
    void answersCreatedWithTheStoredGame() throws Exception {
        MvcResult result = importing(pgn("Api White", "Api Black"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.white.name").value("Api White"))
                .andExpect(jsonPath("$.white.playerId").isNotEmpty())
                .andExpect(jsonPath("$.white.rating").value(1850))
                .andExpect(jsonPath("$.black.name").value("Api Black"))
                .andExpect(jsonPath("$.black.rating").doesNotExist())
                .andExpect(jsonPath("$.event").value("Club Championship"))
                .andExpect(jsonPath("$.site").value("London ENG"))
                .andExpect(jsonPath("$.round").value("3.2"))
                .andExpect(jsonPath("$.playedOn").value("2026-03-14"))
                .andExpect(jsonPath("$.result").value("WHITE_WON"))
                .andExpect(jsonPath("$.eco").value("C60"))
                .andExpect(jsonPath("$.source").value("PGN_IMPORT"))
                .andExpect(jsonPath("$.movetext").value("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6"))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asString();

        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/games/" + id);
    }

    /**
     * A 201 that reported an id for a row that was never written would satisfy
     * every assertion above, so the identifier is followed back to the database.
     */
    @Test
    void persistsTheGameItReports() throws Exception {
        String body = importing(pgn("Persisted White", "Persisted Black"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asString());

        assertThat(games.findById(id)).isPresent();
    }

    /**
     * ADR 0002 makes source_pgn provenance that nothing reads to answer a product
     * question. Putting it in the resource representation would contradict that,
     * and would ship the moves twice.
     */
    @Test
    void doesNotExposeTheSubmittedDocument() throws Exception {
        importing(pgn("Hidden White", "Hidden Black"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourcePgn").doesNotExist())
                .andExpect(jsonPath("$.pgn").doesNotExist());
    }

    /**
     * {@code doesNotExist()} passes whether a field is null or entirely absent, so
     * it cannot tell "present as null" from "omitted" apart — it would stay green
     * even if {@code GameResponse} grew {@code @JsonInclude(NON_NULL)}, or the
     * application ever set {@code spring.jackson.default-property-inclusion:
     * non_null} for an unrelated reason. Either change would silently drop
     * {@code site}, {@code eco}, {@code round}, {@code playedOn} and both ratings
     * from every response, contradicting the documented contract that "a client
     * sees one shape whatever the document said." {@code nullValue()} is the
     * matcher that actually distinguishes the two: it fails on an absent path and
     * passes only when the path is present and null.
     */
    @Test
    void answersOptionalMetadataAsNullRatherThanOmittingIt() throws Exception {
        String noOptionalTags = """
                [White "Null Shape White"]
                [Black "Null Shape Black"]
                [Result "1-0"]

                1. e4 e5 1-0
                """;

        importing(noOptionalTags)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.site").value(nullValue()))
                .andExpect(jsonPath("$.eco").value(nullValue()))
                .andExpect(jsonPath("$.round").value(nullValue()));
    }

    /**
     * The fixture and its ply are taken from ChesslibPgnParserTest, where the same
     * document is already pinned to ILLEGAL_MOVE at ply 5 — e4 cannot reach e6.
     */
    @Test
    void answersUnprocessableContentWithTheCodeAndPlyForAnIllegalMove() throws Exception {
        String illegal = """
                [White "Reject Illegal White"]
                [Black "Reject Illegal Black"]
                [Result "*"]

                1. e4 e5 2. Nf3 Nc6 3. e6 *
                """;

        importing(illegal)
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/errors/invalid-pgn"))
                .andExpect(jsonPath("$.title").value("Invalid PGN"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.code").value("ILLEGAL_MOVE"))
                .andExpect(jsonPath("$.ply").value(5));
    }

    /**
     * A rejection that is not about a specific move omits ply rather than sending
     * null, so a client can branch on presence.
     */
    @Test
    void omitsPlyWhenTheProblemIsNotAboutAMove() throws Exception {
        String noMoves = """
                [White "Reject Moveless White"]
                [Black "Reject Moveless Black"]
                [Result "*"]

                *
                """;

        importing(noMoves)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_MOVES"))
                .andExpect(jsonPath("$.ply").doesNotExist());
    }

    @Test
    void rejectsADocumentThatNamesNoPlayer() throws Exception {
        String unknown = """
                [White "?"]
                [Black "Reject Unknown Black"]
                [Result "1-0"]

                1. e4 e5 1-0
                """;

        importing(unknown)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PLAYER_UNKNOWN"));
    }

    @Test
    void rejectsAFileHoldingMoreThanOneGame() throws Exception {
        String two = """
                [White "Reject Multi White"]
                [Black "Reject Multi Black"]
                [Result "1-0"]

                1. e4 e5 1-0

                [White "Reject Multi White Two"]
                [Black "Reject Multi Black Two"]
                [Result "0-1"]

                1. d4 d5 0-1
                """;

        importing(two)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MULTIPLE_GAMES"));
    }

    @Test
    void rejectsAGameThatDeclaresNoResult() throws Exception {
        String none = """
                [White "Reject Resultless White"]
                [Black "Reject Resultless Black"]

                1. e4 e5
                """;

        importing(none)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESULT_MISSING"));
    }

    @Test
    void treatsAnAbsentPgnFieldAsAnEmptyDocument() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_PGN"));
    }

    /**
     * U+2028 is a line terminator that Java's \\R matches but
     * Character.isISOControl does not, so PgnTagValues lets it through where
     * GameValues would reject it. It cannot reach domain construction: the tag is
     * split across two lines, matches the tag pattern on neither, and the orphaned
     * fragments land in the movetext section where chesslib fails on them.
     *
     * <p>The assertion is that this is a 422 and not a 500 — that the document
     * never reaches NewGame. Verified empirically against ChesslibPgnParser before
     * this test was written; the first version of the analysis had the mechanism
     * wrong while reaching the right conclusion, which is why it is pinned here.
     */
    @Test
    void rejectsALineSeparatorInATagRatherThanFailingInsideTheDomain() throws Exception {
        String separator = """
                [Event "Club%sChampionship"]
                [White "Reject Separator White"]
                [Black "Reject Separator Black"]
                [Result "1-0"]

                1. e4 e5 1-0
                """.formatted(Character.toString(0x2028));

        importing(separator)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_PGN"));
    }

    /**
     * The assertion that spring.mvc.problemdetails.enabled took effect. Without it
     * this body is the legacy {"timestamp","error","path"} shape, and a client
     * written against problem+json would mis-handle it.
     */
    @Test
    void answersBadRequestInProblemJsonForABodyThatIsNotJson() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pgn\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void answersBadRequestForAnEmptyBody() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * One code unit past the cap. This bounds the work the parser can be asked to
     * do; it does not bound bytes received, because Jackson has already
     * deserialised the body by the time validation runs.
     */
    @Test
    void answersBadRequestForADocumentPastTheSizeCap() throws Exception {
        importing("x".repeat(1_048_577))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * Exactly at the cap, not past it. {@code @Size(max = 1_048_576)} must accept
     * this length rather than rejecting it as an off-by-one would; a megabyte of
     * "x" is still not a PGN document, so the request reaches the parser and comes
     * back 422 {@code NOT_PGN} rather than 400.
     */
    @Test
    void acceptsADocumentExactlyAtTheSizeCap() throws Exception {
        importing("x".repeat(1_048_576))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_PGN"));
    }

    @Test
    void answersUnsupportedMediaTypeForANonJsonContentType() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(pgn("Unsupported White", "Unsupported Black")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void answersMethodNotAllowedForAnUnsupportedMethod() throws Exception {
        mockMvc.perform(put("/api/games"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /** The same document with a caller-chosen event, which is how a list test scopes itself. */
    private static String pgnWithEvent(String white, String black, String event) {
        return pgnWithEvent(white, black, event, "2026.03.14");
    }

    /** As above, with a caller-chosen date, for a test that needs two games to order. */
    private static String pgnWithEvent(String white, String black, String event, String date) {
        return """
                [Event "%s"]
                [Site "London ENG"]
                [Date "%s"]
                [Round "3.2"]
                [White "%s"]
                [Black "%s"]
                [Result "1-0"]
                [WhiteElo "1850"]
                [ECO "C60"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0
                """.formatted(event, date, white, black);
    }

    private String importForListing(String event) throws Exception {
        return importing(pgnWithEvent("List White " + event, "List Black " + event, event))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void listsMatchingGamesWithTheDocumentedDefaults() throws Exception {
        String event = "Listing " + UUID.randomUUID();
        importForListing(event);

        mockMvc.perform(get("/api/games").param("event", event))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].event").value(event))
                .andExpect(jsonPath("$.content[0].white.name").value("List White " + event))
                .andExpect(jsonPath("$.content[0].white.rating").value(1850))
                .andExpect(jsonPath("$.content[0].black.rating").doesNotExist())
                .andExpect(jsonPath("$.content[0].playedOn").value("2026-03-14"))
                .andExpect(jsonPath("$.content[0].result").value("WHITE_WON"))
                .andExpect(jsonPath("$.content[0].eco").value("C60"))
                .andExpect(jsonPath("$.content[0].source").value("PGN_IMPORT"));
    }

    /**
     * A page of 25 rows would otherwise carry 25 complete move lists to render a
     * table that shows none of them.
     */
    @Test
    void doesNotCarryTheMovesOnAListRow() throws Exception {
        String event = "Moveless " + UUID.randomUUID();
        importForListing(event);

        mockMvc.perform(get("/api/games").param("event", event))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].movetext").doesNotExist())
                .andExpect(jsonPath("$.content[0].sourcePgn").doesNotExist());
    }

    @Test
    void filtersByPlayerAndColour() throws Exception {
        String event = "Coloured " + UUID.randomUUID();
        String body = importForListing(event);
        String whitePlayerId = objectMapper.readTree(body).get("white").get("playerId").asString();

        mockMvc.perform(get("/api/games")
                        .param("playerId", whitePlayerId)
                        .param("colour", "WHITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].event").value(event));

        mockMvc.perform(get("/api/games")
                        .param("playerId", whitePlayerId)
                        .param("colour", "BLACK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void answersAnEmptyPageWhenNothingMatches() throws Exception {
        mockMvc.perform(get("/api/games").param("event", "Nothing " + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    /**
     * The whole point of GameSort being an enum: an unknown sort field fails in
     * conversion, before a query exists, rather than being concatenated into one.
     */
    @Test
    void rejectsASortFieldOutsideTheWhitelist() throws Exception {
        mockMvc.perform(get("/api/games").param("sort", "movetext"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsAColourWithNoPlayerToNarrow() throws Exception {
        mockMvc.perform(get("/api/games").param("colour", "WHITE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsAnUnsatisfiableDateRange() throws Exception {
        mockMvc.perform(get("/api/games")
                        .param("from", "2026-06-01")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    /**
     * The unsatisfiable-range test above proves the cross-field rule fires, but it
     * cannot prove from and to reach the query at all — it would answer 400 just the
     * same against a date filter that was never wired through. This asserts the
     * filter actually filters.
     */
    @Test
    void filtersByDateRange() throws Exception {
        String event = "Dated " + UUID.randomUUID();
        importForListing(event);

        mockMvc.perform(get("/api/games")
                        .param("event", event)
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].playedOn").value("2026-03-14"));

        mockMvc.perform(get("/api/games")
                        .param("event", event)
                        .param("from", "2026-04-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void rejectsAPageSizeBeyondTheCap() throws Exception {
        mockMvc.perform(get("/api/games").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAPageOfNoRows() throws Exception {
        mockMvc.perform(get("/api/games").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsANegativePage() throws Exception {
        mockMvc.perform(get("/api/games").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAMalformedPlayerIdentifier() throws Exception {
        mockMvc.perform(get("/api/games").param("playerId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAResultOutsideTheEnum() throws Exception {
        mockMvc.perform(get("/api/games").param("result", "WHITE_LOST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnEventTermBeyondTheLengthCap() throws Exception {
        mockMvc.perform(get("/api/games").param("event", "x".repeat(256)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Every other parameter is proven to bind by a 400 that could not occur if it
     * were silently ignored. direction had neither a positive nor a negative test,
     * so renaming the record component would have pinned every list request to DESC
     * with the whole suite still green. This asserts the order actually flips.
     */
    @Test
    void bindsTheSortDirection() throws Exception {
        String event = "Directional " + UUID.randomUUID();
        importing(pgnWithEvent("Dir Early " + event, "Dir Early Black " + event, event,
                "2026.01.05")).andExpect(status().isCreated());
        importing(pgnWithEvent("Dir Late " + event, "Dir Late Black " + event, event,
                "2026.09.20")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/games").param("event", event).param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].playedOn").value("2026-09-20"))
                .andExpect(jsonPath("$.content[1].playedOn").value("2026-01-05"));

        mockMvc.perform(get("/api/games").param("event", event).param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].playedOn").value("2026-01-05"))
                .andExpect(jsonPath("$.content[1].playedOn").value("2026-09-20"));
    }

    @Test
    void rejectsASortDirectionOutsideTheEnum() throws Exception {
        mockMvc.perform(get("/api/games").param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * The plainest request there is, and the only one that reaches the query with no
     * predicates at all. The class shares a container with no cleanup, so this
     * asserts only what is independent of what other tests created: the status, the
     * envelope, and the documented defaults. Content is asserted by the scoped tests.
     */
    @Test
    void answersAParameterlessRequestWithTheEnvelopeAndTheDefaults() throws Exception {
        importForListing("Unscoped " + UUID.randomUUID());

        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    /** Imports a game and reports the identifier the endpoint assigned it. */
    private String importForDetail(String label) throws Exception {
        String body = importing(pgn("Detail White " + label, "Detail Black " + label))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asString();
    }

    /**
     * The moves are the reason this endpoint exists: the list row deliberately has
     * no movetext, so a viewer that opens a game has nowhere else to get them.
     */
    @Test
    void answersTheStoredGameForItsIdentifier() throws Exception {
        String label = UUID.randomUUID().toString();
        String id = importForDetail(label);

        mockMvc.perform(get("/api/games/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.white.name").value("Detail White " + label))
                .andExpect(jsonPath("$.black.name").value("Detail Black " + label))
                .andExpect(jsonPath("$.white.rating").value(1850))
                .andExpect(jsonPath("$.black.rating").doesNotExist())
                .andExpect(jsonPath("$.event").value("Club Championship"))
                .andExpect(jsonPath("$.playedOn").value("2026-03-14"))
                .andExpect(jsonPath("$.result").value("WHITE_WON"))
                .andExpect(jsonPath("$.eco").value("C60"))
                .andExpect(jsonPath("$.source").value("PGN_IMPORT"))
                .andExpect(jsonPath("$.movetext").value("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6"));
    }

    /**
     * The Location header POST already returns has pointed at nothing until now.
     * Following it is what makes the created resource actually retrievable, and this
     * fails if either side of that contract drifts.
     */
    @Test
    void answersTheGameAtTheLocationTheImportReported() throws Exception {
        MvcResult created = importing(pgn("Located White " + UUID.randomUUID(),
                        "Located Black " + UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asString();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    /**
     * problem+json rather than an empty body: application.yml turns problem details
     * on so that one API has one error shape, and a bare 404 would be the exception.
     *
     * <p>The detail is asserted because without it this test cannot fail. An
     * unmapped path already answers 404 problem+json from the static-resource
     * fallback, so status and content type alone pass just as well against an
     * endpoint that does not exist. The message is what proves a handler ran.
     */
    @Test
    void answersNotFoundForAnIdentifierThatMatchesNoGame() throws Exception {
        mockMvc.perform(get("/api/games/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No game with that identifier"));
    }

    /**
     * 400 rather than 404, matching the malformed playerId on the list endpoint: an
     * identifier that is not a UUID is a bad request, not a game that is absent, and
     * a client can tell the two apart.
     */
    @Test
    void rejectsAMalformedGameIdentifier() throws Exception {
        mockMvc.perform(get("/api/games/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * The limit of the 400/404 split above, pinned rather than left to be
     * rediscovered. {@code UUID.fromString} accepts non-canonical dash-separated
     * forms and widens each group, so this identifier parses, misses, and is
     * reported as a game that is not here rather than as a malformed request.
     *
     * <p>This documents current behaviour; it passed the first time it was run. It
     * is here so that a later change to strict parsing is a deliberate one that
     * breaks a test, rather than a silent change to what a client is told.
     */
    @Test
    void treatsANonCanonicalIdentifierThatStillParsesAsAMiss() throws Exception {
        mockMvc.perform(get("/api/games/1-1-1-1-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No game with that identifier"));
    }

    /**
     * ADR 0002 again, on the endpoint most tempted to leak it: the detail response
     * is where a submitted document would look most at home.
     */
    @Test
    void doesNotExposeTheSubmittedDocumentOnTheDetail() throws Exception {
        String id = importForDetail(UUID.randomUUID().toString());

        mockMvc.perform(get("/api/games/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourcePgn").doesNotExist())
                .andExpect(jsonPath("$.pgn").doesNotExist());
    }
}
