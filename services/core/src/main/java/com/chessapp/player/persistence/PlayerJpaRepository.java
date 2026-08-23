package com.chessapp.player.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface PlayerJpaRepository extends Repository<PlayerEntity, UUID> {

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
     * <p><b>{@code clearAutomatically = true} detaches everything in the current
     * persistence context, not just this entity.</b> That is required here: the
     * subsequent read in {@link PlayerRepositoryAdapter#createOrFind} must hit the
     * database rather than a stale first-level-cache miss for a row this same
     * transaction just inserted. But because {@code createOrFind} normally joins
     * an existing transaction (default {@code REQUIRED} propagation) rather than
     * starting its own, calling it mid-transaction detaches every entity the
     * caller had already loaded there too. A caller holding other entities across
     * a call to {@code createOrFind} must re-read anything it intends to mutate
     * afterwards.
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
