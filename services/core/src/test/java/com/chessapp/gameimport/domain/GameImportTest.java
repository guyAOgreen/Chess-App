package com.chessapp.gameimport.domain;

import static com.chessapp.gameimport.domain.GameImportStatus.CONFIRMED;
import static com.chessapp.gameimport.domain.GameImportStatus.FAILED;
import static com.chessapp.gameimport.domain.GameImportStatus.PROCESSING;
import static com.chessapp.gameimport.domain.GameImportStatus.READY_FOR_REVIEW;
import static com.chessapp.gameimport.domain.GameImportStatus.UPLOADED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
        GameImport gameImport = new GameImport(ID, FAILED, "\u00A0\u200B scoresheet unreadable \u202F\n");

        assertThat(gameImport.failureReason()).isEqualTo("scoresheet unreadable");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "\u2003", "\u3000 ", "\u00A0", "\u2007\u202F", "\u200B", "\uFEFF", "--"})
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

    /** A valid import in {@code status}; a failed one needs a reason to exist. */
    private static GameImport inStatus(GameImportStatus status) {
        return new GameImport(ID, status, status == FAILED ? "scoresheet unreadable" : null);
    }

    @Test
    void startProcessingMovesAnUploadedImportToProcessing() {
        GameImport processing = inStatus(UPLOADED).startProcessing();

        assertThat(processing).isEqualTo(new GameImport(ID, PROCESSING, null));
    }

    @Test
    void markReadyForReviewMovesAProcessingImportToReadyForReview() {
        GameImport ready = inStatus(PROCESSING).markReadyForReview();

        assertThat(ready).isEqualTo(new GameImport(ID, READY_FOR_REVIEW, null));
    }

    @Test
    void failMovesAProcessingImportToFailedWithATrimmedReason() {
        GameImport failed = inStatus(PROCESSING).fail("  recognition timed out ");

        assertThat(failed).isEqualTo(new GameImport(ID, FAILED, "recognition timed out"));
    }

    @Test
    void confirmMovesAnImportReadyForReviewToConfirmed() {
        GameImport confirmed = inStatus(READY_FOR_REVIEW).confirm();

        assertThat(confirmed).isEqualTo(new GameImport(ID, CONFIRMED, null));
    }

    @Test
    void aTransitionReturnsANewSnapshotAndLeavesTheOriginalUnchanged() {
        GameImport uploaded = inStatus(UPLOADED);

        GameImport processing = uploaded.startProcessing();

        assertThat(processing).isNotSameAs(uploaded);
        assertThat(uploaded.status()).isEqualTo(UPLOADED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\u2003", "\u00A0", "\u200B"})
    void failRequiresAReasonSoAFailureAlwaysSaysWhy(String reason) {
        assertThatThrownBy(() -> inStatus(PROCESSING).fail(reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }

    @ParameterizedTest
    @EnumSource(value = GameImportStatus.class, names = "PROCESSING", mode = EnumSource.Mode.EXCLUDE)
    void failFromAnyOtherStatusReportsTheTransitionBeforeCheckingTheReason(GameImportStatus from) {
        assertThatThrownBy(() -> inStatus(from).fail(null))
                .isInstanceOf(InvalidGameImportTransition.class);
    }

    private record Operation(String name, GameImportStatus allowedFrom, GameImportStatus to,
                             UnaryOperator<GameImport> apply) {
    }

    private static final Operation[] OPERATIONS = {
            new Operation("startProcessing", UPLOADED, PROCESSING, GameImport::startProcessing),
            new Operation("markReadyForReview", PROCESSING, READY_FOR_REVIEW, GameImport::markReadyForReview),
            new Operation("fail", PROCESSING, FAILED, gameImport -> gameImport.fail("recognition timed out")),
            new Operation("confirm", READY_FOR_REVIEW, CONFIRMED, GameImport::confirm),
    };

    /** Every operation paired with every status it must not be called from, self-transitions included. */
    static Stream<Arguments> everyDisallowedSourceState() {
        return Arrays.stream(OPERATIONS)
                .flatMap(operation -> Arrays.stream(GameImportStatus.values())
                        .filter(from -> from != operation.allowedFrom())
                        .map(from -> Arguments.of(operation.name(), from, operation)));
    }

    @ParameterizedTest(name = "{0} from {1}")
    @MethodSource("everyDisallowedSourceState")
    void rejectsAnOperationFromAStatusItIsNotAllowedFrom(String name, GameImportStatus from, Operation operation) {
        InvalidGameImportTransition error = catchThrowableOfType(
                InvalidGameImportTransition.class, () -> operation.apply().apply(inStatus(from)));

        assertThat(error).isNotNull();
        assertThat(error.from()).isEqualTo(from);
        assertThat(error.to()).isEqualTo(operation.to());
        assertThat(error).hasMessageContaining(from.name()).hasMessageContaining(operation.to().name());
    }
}
