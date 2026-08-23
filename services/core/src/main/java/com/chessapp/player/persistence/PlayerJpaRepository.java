package com.chessapp.player.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlayerJpaRepository extends JpaRepository<PlayerEntity, java.util.UUID> {

    Optional<PlayerEntity> findByDisplayName(String displayName);

    /**
     * Inserts unless the display name is already taken.
     *
     * <p>{@code ON CONFLICT (display_name) DO NOTHING} means a losing concurrent
     * insert neither raises an error nor aborts the transaction, so the caller can
     * simply read afterwards. A {@code fide_id} collision is deliberately not
     * covered by the conflict target: that is contradictory data, not a race, and
     * must surface.
     *
     * @return 1 when this call inserted the row, 0 when it already existed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO players (display_name, fide_id, federation)
            VALUES (:displayName, :fideId, :federation)
            ON CONFLICT (display_name) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("displayName") String displayName,
                       @Param("fideId") String fideId,
                       @Param("federation") String federation);
}
