package com.chessapp.gameimport.domain;

import static com.chessapp.gameimport.domain.GameImportStatus.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GameImportTest {

    private static final UUID ID = UUID.fromString("0199a1b2-0000-7000-8000-000000000001");

    @ParameterizedTest
    @EnumSource(value = GameImportStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    void acceptsAnImportInANonFailedStatusWithNoReason(GameImportStatus status) {
        GameImport gameImport = new GameImport(ID, status, null);

        assertThat(gameImport.id()).isEqualTo(ID);
        assertThat(gameImport.status()).isEqualTo(status);
        assertThat(gameImport.failureReason()).isNull();
    }

    @Test
    void rejectsAMissingIdBecauseAnImportIsAlwaysPersisted() {
        assertThatThrownBy(() -> new GameImport(null, GameImportStatus.UPLOADED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejectsAMissingStatus() {
        assertThatThrownBy(() -> new GameImport(ID, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void trimsTheReasonOfAFailedImport() {
        GameImport gameImport = new GameImport(ID, FAILED, "  scoresheet unreadable \n");

        assertThat(gameImport.failureReason()).isEqualTo("scoresheet unreadable");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void rejectsAFailedImportWithoutAReasonSoAFailureAlwaysSaysWhy(String reason) {
        assertThatThrownBy(() -> new GameImport(ID, FAILED, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }

    @ParameterizedTest
    @EnumSource(value = GameImportStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsAReasonOnAnImportThatHasNotFailed(GameImportStatus status) {
        assertThatThrownBy(() -> new GameImport(ID, status, "scoresheet unreadable"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsEvenABlankReasonOnAnImportThatHasNotFailed(String reason) {
        assertThatThrownBy(() -> new GameImport(ID, GameImportStatus.READY_FOR_REVIEW, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }
}
