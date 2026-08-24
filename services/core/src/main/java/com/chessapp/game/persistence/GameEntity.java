package com.chessapp.game.persistence;

import com.chessapp.game.domain.GameResult;
import com.chessapp.game.domain.GameSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read mapping for the {@code games} table.
 *
 * <p>Deliberately has no {@code @GeneratedValue} and no setters: rows are created
 * by the native insert in {@link GameRepositoryAdapter}, never by {@code save()},
 * so Hibernate is never asked to populate a database-defaulted identifier on
 * flush. This also keeps ADR 0002's rule that {@code movetext} is immutable
 * structural rather than a matter of discipline — there is nothing here to mutate.
 */
@Entity
@Table(name = "games")
class GameEntity {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "white_player_id", nullable = false)
    private UUID whitePlayerId;

    @Column(name = "black_player_id", nullable = false)
    private UUID blackPlayerId;

    @Column(name = "white_name", nullable = false)
    private String whiteName;

    @Column(name = "black_name", nullable = false)
    private String blackName;

    @Column(name = "white_rating")
    private Integer whiteRating;

    @Column(name = "black_rating")
    private Integer blackRating;

    @Column(name = "event")
    private String event;

    @Column(name = "site")
    private String site;

    @Column(name = "round")
    private String round;

    @Column(name = "played_on")
    private LocalDate playedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private GameResult result;

    @Column(name = "eco")
    private String eco;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private GameSource source;

    @Column(name = "movetext", nullable = false)
    private String movetext;

    @Column(name = "source_pgn")
    private String sourcePgn;

    protected GameEntity() {
        // required by JPA
    }

    UUID getId() {
        return id;
    }

    UUID getWhitePlayerId() {
        return whitePlayerId;
    }

    UUID getBlackPlayerId() {
        return blackPlayerId;
    }

    String getWhiteName() {
        return whiteName;
    }

    String getBlackName() {
        return blackName;
    }

    Integer getWhiteRating() {
        return whiteRating;
    }

    Integer getBlackRating() {
        return blackRating;
    }

    String getEvent() {
        return event;
    }

    String getSite() {
        return site;
    }

    String getRound() {
        return round;
    }

    LocalDate getPlayedOn() {
        return playedOn;
    }

    GameResult getResult() {
        return result;
    }

    String getEco() {
        return eco;
    }

    GameSource getSource() {
        return source;
    }

    String getMovetext() {
        return movetext;
    }

    String getSourcePgn() {
        return sourcePgn;
    }
}
