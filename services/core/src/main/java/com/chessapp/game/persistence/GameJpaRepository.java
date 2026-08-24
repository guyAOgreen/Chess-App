package com.chessapp.game.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface GameJpaRepository extends Repository<GameEntity, UUID> {

    Optional<GameEntity> findById(UUID id);

    /**
     * Inserts a game and returns the identifier the database generated.
     *
     * <p>Native, and {@code RETURNING id} rather than {@code save()}: ADR 0002
     * makes the primary key a {@code uuidv7()} column default so that generation
     * stays at the persistence boundary. Hibernate has no clean mapping for a
     * database-defaulted identifier on an entity it inserts, so the insert is
     * written out here and the generated value read straight back from the same
     * statement. That also means no second query to fetch the row.
     *
     * <p>The enums are bound as their names, matching the {@code TEXT} columns and
     * the {@code CHECK} constraints in the migration.
     */
    @Query(value = """
            INSERT INTO games (white_player_id, black_player_id, white_name, black_name,
                               white_rating, black_rating, event, site, round, played_on,
                               result, eco, source, movetext, source_pgn)
            VALUES (:whitePlayerId, :blackPlayerId, :whiteName, :blackName,
                    :whiteRating, :blackRating, :event, :site, :round, :playedOn,
                    :result, :eco, :source, :movetext, :sourcePgn)
            RETURNING id
            """, nativeQuery = true)
    UUID insertReturningId(@Param("whitePlayerId") UUID whitePlayerId,
                           @Param("blackPlayerId") UUID blackPlayerId,
                           @Param("whiteName") String whiteName,
                           @Param("blackName") String blackName,
                           @Param("whiteRating") Integer whiteRating,
                           @Param("blackRating") Integer blackRating,
                           @Param("event") String event,
                           @Param("site") String site,
                           @Param("round") String round,
                           @Param("playedOn") LocalDate playedOn,
                           @Param("result") String result,
                           @Param("eco") String eco,
                           @Param("source") String source,
                           @Param("movetext") String movetext,
                           @Param("sourcePgn") String sourcePgn);
}
