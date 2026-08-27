package com.chessapp.game.persistence;

import com.chessapp.game.domain.GameColour;
import com.chessapp.game.domain.GamePage;
import com.chessapp.game.domain.GameQuery;
import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSide;
import com.chessapp.game.domain.GameSource;
import com.chessapp.game.domain.GameSummary;
import com.chessapp.game.domain.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one query in this application whose shape varies with the request.
 *
 * <p>Everything else runs a fixed statement binding every value as a named
 * parameter. A list endpoint cannot: which {@code WHERE} clauses exist depends on
 * which filters were supplied, and {@code ORDER BY} cannot be a bound parameter at
 * all. So only values bind here, and the structure comes from the enums and typed
 * fields of {@link GameQuery} — never from a request string.
 *
 * <p>Written as Criteria rather than as native SQL with assembled fragments,
 * because assembled fragments are structural string concatenation however carefully
 * whitelisted, and because two hand-written statements — rows and count — drift.
 * Spring Data {@code Specification} was the alternative; it was declined so that
 * the null precedence below is stated here rather than delegated to a translation
 * that would have to be verified.
 */
@Component
class GameSearchQuery {

    private final EntityManager entityManager;

    GameSearchQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    GamePage run(GameQuery query) {
        return new GamePage(rows(query), query.page(), query.size(), total(query));
    }

    /**
     * Projects the summary columns rather than the entity. {@code movetext} is
     * stored out of line for longer games, so selecting it for every row of every
     * page would cost a fetch per row to render a table that shows none of it.
     *
     * <p>Selected as a {@code Tuple} and mapped by hand: {@link GameSummary} holds a
     * {@link GameSide} per colour, and the specification does not allow a compound
     * selection as an argument to another, so a nested {@code construct} is not
     * portable.
     */
    private List<GameSummary> rows(GameQuery query) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> criteria = builder.createTupleQuery();
        Root<GameEntity> game = criteria.from(GameEntity.class);

        criteria.multiselect(List.<Selection<?>>of(game.get("id"),
                        game.get("whitePlayerId"), game.get("whiteName"), game.get("whiteRating"),
                        game.get("blackPlayerId"), game.get("blackName"), game.get("blackRating"),
                        game.get("event"), game.get("site"), game.get("round"),
                        game.get("playedOn"), game.get("result"), game.get("eco"),
                        game.get("source")))
                .where(filters(builder, game, query))
                .orderBy(ordering(builder, game, query));

        return entityManager.createQuery(criteria)
                .setFirstResult(query.page() * query.size())
                .setMaxResults(query.size())
                .getResultList()
                .stream()
                .map(GameSearchQuery::toSummary)
                .toList();
    }

    private long total(GameQuery query) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
        Root<GameEntity> game = criteria.from(GameEntity.class);

        criteria.select(builder.count(game)).where(filters(builder, game, query));

        return entityManager.createQuery(criteria).getSingleResult();
    }

    /**
     * Called once per query with that query's own {@link Root}. A Criteria
     * {@link Predicate} belongs to the root that produced it, so the rows query and
     * the count query cannot share instances — sharing this method is what stops
     * their filtering from drifting, and a rows query and a count query that
     * disagree are a silently wrong answer rather than a failure.
     */
    private static Predicate[] filters(CriteriaBuilder builder, Root<GameEntity> game,
                                       GameQuery query) {
        List<Predicate> filters = new ArrayList<>();

        UUID playerId = query.playerId();
        if (playerId != null) {
            filters.add(playerFilter(builder, game, playerId, query.colour()));
        }
        GameResult result = query.result();
        if (result != null) {
            filters.add(builder.equal(game.get("result"), result));
        }
        LocalDate from = query.from();
        if (from != null) {
            filters.add(builder.greaterThanOrEqualTo(game.get("playedOn"), from));
        }
        LocalDate to = query.to();
        if (to != null) {
            filters.add(builder.lessThanOrEqualTo(game.get("playedOn"), to));
        }
        String event = query.event();
        if (event != null) {
            filters.add(builder.like(builder.lower(game.get("event")),
                    LikePattern.containing(event), LikePattern.ESCAPE));
        }
        return filters.toArray(new Predicate[0]);
    }

    /**
     * Without a colour this is an OR across both player columns, which PostgreSQL
     * serves as a BitmapOr over {@code games_white_player_played_on_idx} and
     * {@code games_black_player_played_on_idx} rather than as a scan.
     */
    private static Predicate playerFilter(CriteriaBuilder builder, Root<GameEntity> game,
                                          UUID playerId, GameColour colour) {
        Predicate asWhite = builder.equal(game.get("whitePlayerId"), playerId);
        Predicate asBlack = builder.equal(game.get("blackPlayerId"), playerId);
        return switch (colour) {
            case null -> builder.or(asWhite, asBlack);
            case WHITE -> asWhite;
            case BLACK -> asBlack;
        };
    }

    /**
     * Nulls last in both directions. {@code played_on} is null whenever a PGN date
     * was only partly known, and PostgreSQL sorts nulls first under {@code DESC}, so
     * the default would put every undated game ahead of the most recent dated one.
     * The indexes are declared {@code DESC NULLS LAST} for that reason, and
     * descending therefore matches {@code games_played_on_idx} exactly. Ascending
     * cannot be served by it — a backwards scan yields nulls first — and pays for a
     * sort instead, so that reversing the order does not send undated games to the
     * top of the list.
     *
     * <p>The tie-break is not decoration. Without it two games sharing a
     * {@code played_on} have no defined relative order, and paging across them can
     * repeat one row while skipping another. {@code id} is a {@code uuidv7()}, so it
     * is time-ordered and reads as "most recently imported first" under
     * {@code DESC}.
     */
    private static List<Order> ordering(CriteriaBuilder builder, Root<GameEntity> game,
                                        GameQuery query) {
        String attribute = switch (query.sort()) {
            case PLAYED_ON -> "playedOn";
        };
        return List.of(order(builder, game.get(attribute), query.direction()),
                order(builder, game.get("id"), query.direction()));
    }

    private static Order order(CriteriaBuilder builder, Path<?> path, SortDirection direction) {
        return switch (direction) {
            case ASC -> builder.asc(path, Nulls.LAST);
            case DESC -> builder.desc(path, Nulls.LAST);
        };
    }

    /** Positional, matching the {@code multiselect} order above. */
    private static GameSummary toSummary(Tuple row) {
        return new GameSummary(row.get(0, UUID.class),
                new GameSide(row.get(1, UUID.class), row.get(2, String.class),
                        row.get(3, Integer.class)),
                new GameSide(row.get(4, UUID.class), row.get(5, String.class),
                        row.get(6, Integer.class)),
                row.get(7, String.class),
                row.get(8, String.class),
                row.get(9, String.class),
                row.get(10, LocalDate.class),
                row.get(11, GameResult.class),
                row.get(12, String.class),
                row.get(13, GameSource.class));
    }
}
