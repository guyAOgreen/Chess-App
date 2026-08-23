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
     * <p>{@code flushAutomatically} orders any pending writes ahead of this native
     * statement. The persistence context is deliberately NOT cleared: the read
     * that follows in {@link PlayerRepositoryAdapter#createOrFind} is a derived
     * query, which always goes to the database rather than consulting the
     * first-level cache, and no managed instance can shadow the inserted row —
     * the context has never seen it. Clearing would detach every entity the
     * caller had loaded, which is a side effect this method has no business
     * having.
     *
     * @return 1 when this call inserted the row, 0 when it already existed
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO players (display_name, fide_id, federation)
            VALUES (:displayName, :fideId, :federation)
            ON CONFLICT (display_name) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("displayName") String displayName,
                       @Param("fideId") String fideId,
                       @Param("federation") String federation);
}
