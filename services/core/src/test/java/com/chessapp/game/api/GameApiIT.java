package com.chessapp.game.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

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

    /**
     * The container is shared across the class with no cleanup between methods, so
     * each test names its own players.
     */
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
        importing(pgn("Api White", "Api Black"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/games/")))
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
                .andExpect(jsonPath("$.movetext").value("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6"));
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

        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());

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
}
