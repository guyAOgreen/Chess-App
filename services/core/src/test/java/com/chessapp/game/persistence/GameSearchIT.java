package com.chessapp.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chessapp.game.domain.GameColour;
import com.chessapp.game.domain.GamePage;
import com.chessapp.game.domain.GameQuery;
import com.chessapp.game.domain.GameRepository;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSort;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.GameSummary;
import com.chessapp.game.domain.NewGame;
import com.chessapp.game.domain.SortDirection;
import com.chessapp.player.domain.NewPlayer;
import com.chessapp.player.domain.PlayerRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The container is shared across the class with no cleanup between methods, so an
 * unfiltered query would see every game every other test created. Each test
 * therefore gets its own pair of players in {@link #createPlayers()} and scopes
 * every query to them.
 */
@Testcontainers
@SpringBootTest
class GameSearchIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private GameRepository games;

    @Autowired
    private PlayerRepository players;

    private UUID subject;
    private UUID opponent;

    @BeforeEach
    void createPlayers() {
        String unique = UUID.randomUUID().toString();
        subject = players.createOrFind(new NewPlayer("Subject " + unique, null, null)).id();
        opponent = players.createOrFind(new NewPlayer("Opponent " + unique, null, null)).id();
    }

    private UUID store(UUID whitePlayerId, UUID blackPlayerId,
                       LocalDate playedOn, GameResult result, String event) {
        return games.save(new NewGame(
                new GameSide(whitePlayerId, "White " + whitePlayerId, null),
                new GameSide(blackPlayerId, "Black " + blackPlayerId, null),
                event, null, null, playedOn, result, null, GameSource.PGN_IMPORT,
                "1. e4 e5", null)).id();
    }

    private UUID storeDated(LocalDate playedOn) {
        return store(subject, opponent, playedOn, GameResult.DRAW, null);
    }

    private GameQuery filtered(GameColour colour, GameResult result,
                               LocalDate from, LocalDate to, String event) {
        return new GameQuery(subject, colour, result, from, to, event,
                GameSort.PLAYED_ON, SortDirection.DESC, 0, 50);
    }

    private GameQuery all() {
        return filtered(null, null, null, null, null);
    }

    private GameQuery sorted(SortDirection direction) {
        return new GameQuery(subject, null, null, null, null, null,
                GameSort.PLAYED_ON, direction, 0, 50);
    }

    private GameQuery paged(int page, int size) {
        return new GameQuery(subject, null, null, null, null, null,
                GameSort.PLAYED_ON, SortDirection.DESC, page, size);
    }

    private static List<UUID> ids(GamePage page) {
        return page.content().stream().map(GameSummary::id).toList();
    }

    @Test
    void aPlayerFilterAloneMatchesGamesOfEitherColour() {
        UUID asWhite = store(subject, opponent, LocalDate.of(2026, 1, 1), GameResult.DRAW, null);
        UUID asBlack = store(opponent, subject, LocalDate.of(2026, 2, 1), GameResult.DRAW, null);

        assertThat(ids(games.find(all()))).containsExactlyInAnyOrder(asWhite, asBlack);
    }

    @Test
    void aColourNarrowsThePlayerFilterToOneSide() {
        UUID asWhite = store(subject, opponent, LocalDate.of(2026, 1, 1), GameResult.DRAW, null);
        store(opponent, subject, LocalDate.of(2026, 2, 1), GameResult.DRAW, null);

        assertThat(ids(games.find(filtered(GameColour.WHITE, null, null, null, null))))
                .containsExactly(asWhite);
    }

    @Test
    void filtersToOneResult() {
        UUID drawn = store(subject, opponent, LocalDate.of(2026, 1, 1), GameResult.DRAW, null);
        store(subject, opponent, LocalDate.of(2026, 2, 1), GameResult.WHITE_WON, null);

        assertThat(ids(games.find(filtered(null, GameResult.DRAW, null, null, null))))
                .containsExactly(drawn);
    }

    @Test
    void treatsBothDateBoundsAsInclusive() {
        UUID first = storeDated(LocalDate.of(2026, 1, 1));
        UUID last = storeDated(LocalDate.of(2026, 6, 30));
        storeDated(LocalDate.of(2025, 12, 31));
        storeDated(LocalDate.of(2026, 7, 1));

        GamePage page = games.find(filtered(null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), null));

        assertThat(ids(page)).containsExactlyInAnyOrder(first, last);
    }

    /**
     * played_on is null whenever a PGN date was only partly known, and no comparison
     * is true of null. A user filtering to 2026 therefore does not see a game that
     * said "2026.??.??" — correct, and exactly the kind of thing reported as a bug,
     * so it is asserted rather than assumed.
     */
    @Test
    void excludesAnUndatedGameFromAnyDateBound() {
        UUID dated = storeDated(LocalDate.of(2026, 3, 14));
        storeDated(null);

        assertThat(ids(games.find(filtered(null, null, LocalDate.of(2026, 1, 1), null, null))))
                .containsExactly(dated);
    }

    @Test
    void matchesAnEventSubstringIgnoringCase() {
        UUID matching = store(subject, opponent, null, GameResult.DRAW, "Obs Club Championship");
        store(subject, opponent, null, GameResult.DRAW, "Kent Open");

        assertThat(ids(games.find(filtered(null, null, null, null, "CHAMPIONSHIP"))))
                .containsExactly(matching);
    }

    /** lower(null) is null and null LIKE anything is null, so an unset event never matches. */
    @Test
    void doesNotMatchAGameWithNoEventAtAll() {
        store(subject, opponent, null, GameResult.DRAW, null);

        assertThat(games.find(filtered(null, null, null, null, "open")).content()).isEmpty();
    }

    @Test
    void matchesAPerCentInAnEventLiterally() {
        UUID literal = store(subject, opponent, null, GameResult.DRAW, "50% Club");
        store(subject, opponent, null, GameResult.DRAW, "500 Club");

        assertThat(ids(games.find(filtered(null, null, null, null, "50%"))))
                .containsExactly(literal);
    }

    @Test
    void matchesAnUnderscoreInAnEventLiterally() {
        UUID literal = store(subject, opponent, null, GameResult.DRAW, "A_B Open");
        store(subject, opponent, null, GameResult.DRAW, "AxB Open");

        assertThat(ids(games.find(filtered(null, null, null, null, "A_B"))))
                .containsExactly(literal);
    }

    @Test
    void combinesEveryFilterWithAnd() {
        UUID wanted = store(subject, opponent,
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "Obs Club Championship");
        store(opponent, subject,
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "Obs Club Championship");
        store(subject, opponent,
                LocalDate.of(2026, 3, 14), GameResult.DRAW, "Obs Club Championship");
        store(subject, opponent,
                LocalDate.of(2020, 3, 14), GameResult.WHITE_WON, "Obs Club Championship");
        store(subject, opponent,
                LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "Kent Open");

        GamePage page = games.find(filtered(GameColour.WHITE, GameResult.WHITE_WON,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "championship"));

        assertThat(ids(page)).containsExactly(wanted);
    }

    /**
     * PostgreSQL sorts nulls first under DESC, and the indexes are declared
     * DESC NULLS LAST precisely so undated games do not lead the list.
     *
     * <p>This is the test that catches a regression to the one-argument
     * {@code desc()}: without the explicit precedence the database's own default
     * puts the undated game first, contradicting the expectation below.
     */
    @Test
    void sortsAnUndatedGameLastWhenDescending() {
        UUID older = storeDated(LocalDate.of(2026, 1, 1));
        UUID newer = storeDated(LocalDate.of(2026, 6, 1));
        UUID undated = storeDated(null);

        assertThat(ids(games.find(sorted(SortDirection.DESC))))
                .containsExactly(newer, older, undated);
    }

    /**
     * Nulls last in both directions is a deliberate product decision: reversing the
     * order must not make undated games jump from the bottom of the list to the top.
     * It costs a sort, because a backwards scan of a DESC NULLS LAST index yields
     * nulls first.
     *
     * <p>This test pins that decision rather than the mechanism. PostgreSQL already
     * defaults ASC to nulls last, so it would pass even against a one-argument
     * {@code asc()} — the regression to the one-argument overload is caught by
     * {@link #sortsAnUndatedGameLastWhenDescending()}, where the database default is
     * nulls first and contradicts the expectation. What this test does catch is the
     * alternative the design rejected: ordering ASC with nulls first to keep the
     * index, which would send undated games to the top of a reversed list.
     *
     * <p>The whole order is asserted, not just the last element, so that a change
     * putting the dated games in the wrong order fails here too.
     */
    @Test
    void sortsAnUndatedGameLastWhenAscendingToo() {
        UUID older = storeDated(LocalDate.of(2026, 1, 1));
        UUID newer = storeDated(LocalDate.of(2026, 6, 1));
        UUID undated = storeDated(null);

        assertThat(ids(games.find(sorted(SortDirection.ASC))))
                .containsExactly(older, newer, undated);
    }

    @Test
    void ordersDatedGamesMostRecentFirstWhenDescending() {
        UUID older = storeDated(LocalDate.of(2026, 1, 1));
        UUID newer = storeDated(LocalDate.of(2026, 6, 1));

        assertThat(ids(games.find(sorted(SortDirection.DESC)))).containsExactly(newer, older);
    }

    /**
     * Without a tie-break, games sharing a played_on have no defined relative order
     * and paging across them can repeat one row while skipping another.
     */
    @Test
    void pagesAcrossGamesSharingADateWithoutRepeatingOrSkippingAny() {
        LocalDate sameDay = LocalDate.of(2026, 3, 14);
        UUID first = storeDated(sameDay);
        UUID second = storeDated(sameDay);
        UUID third = storeDated(sameDay);

        List<UUID> seen = List.copyOf(ids(games.find(paged(0, 2))));
        List<UUID> next = ids(games.find(paged(1, 2)));

        assertThat(seen).hasSize(2);
        assertThat(next).hasSize(1);
        assertThat(seen).doesNotContainAnyElementsOf(next);
        assertThat(List.of(seen, next).stream().flatMap(List::stream).toList())
                .containsExactlyInAnyOrder(first, second, third);
    }

    @Test
    void countsTheFilteredSetRatherThanTheReturnedPage() {
        storeDated(LocalDate.of(2026, 1, 1));
        storeDated(LocalDate.of(2026, 2, 1));
        storeDated(LocalDate.of(2026, 3, 1));

        GamePage page = games.find(paged(0, 2));

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    void returnsAnEmptyPagePastTheEndWhileStillReportingTheTotal() {
        storeDated(LocalDate.of(2026, 1, 1));

        GamePage page = games.find(paged(9, 25));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(9);
    }

    @Test
    void returnsEveryProjectedFieldOfARow() {
        store(subject, opponent, LocalDate.of(2026, 3, 14), GameResult.WHITE_WON, "Obs Club");

        GameSummary row = games.find(all()).content().getFirst();

        assertThat(row.id()).isNotNull();
        assertThat(row.white().playerId()).isEqualTo(subject);
        assertThat(row.white().name()).isEqualTo("White " + subject);
        assertThat(row.black().playerId()).isEqualTo(opponent);
        assertThat(row.event()).isEqualTo("Obs Club");
        assertThat(row.playedOn()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(row.result()).isEqualTo(GameResult.WHITE_WON);
        assertThat(row.source()).isEqualTo(GameSource.PGN_IMPORT);
    }
}
