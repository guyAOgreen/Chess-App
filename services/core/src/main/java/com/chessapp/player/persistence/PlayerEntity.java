package com.chessapp.player.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Read mapping for the {@code players} table.
 *
 * <p>Deliberately has no {@code @GeneratedValue}: rows are created by the native
 * upsert in {@link PlayerRepositoryAdapter}, never by {@code save()}, so Hibernate
 * is never asked to populate a generated identifier on flush. Introducing
 * {@code save()} for a new entity means revisiting that decision.
 */
@Entity
@Table(name = "players")
class PlayerEntity {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "fide_id")
    private String fideId;

    @Column(name = "federation")
    private String federation;

    protected PlayerEntity() {
        // required by JPA
    }

    UUID getId() {
        return id;
    }

    String getDisplayName() {
        return displayName;
    }

    String getFideId() {
        return fideId;
    }

    String getFederation() {
        return federation;
    }
}
