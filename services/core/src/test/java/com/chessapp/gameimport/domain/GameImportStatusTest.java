package com.chessapp.gameimport.domain;

import static com.chessapp.gameimport.domain.GameImportStatus.CONFIRMED;
import static com.chessapp.gameimport.domain.GameImportStatus.FAILED;
import static com.chessapp.gameimport.domain.GameImportStatus.PROCESSING;
import static com.chessapp.gameimport.domain.GameImportStatus.READY_FOR_REVIEW;
import static com.chessapp.gameimport.domain.GameImportStatus.UPLOADED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GameImportStatusTest {

    /** The spec's transition table, written out independently of the code under test. */
    private static final Set<List<GameImportStatus>> ALLOWED = Set.of(
            List.of(UPLOADED, PROCESSING),
            List.of(PROCESSING, READY_FOR_REVIEW),
            List.of(PROCESSING, FAILED),
            List.of(READY_FOR_REVIEW, CONFIRMED));

    static Stream<Arguments> everyPair() {
        return Arrays.stream(GameImportStatus.values())
                .flatMap(from -> Arrays.stream(GameImportStatus.values())
                        .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void allowsExactlyTheTransitionsInTheTable(GameImportStatus from, GameImportStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.contains(List.of(from, to)));
    }

    @Test
    void onlyConfirmedAndFailedAreTerminal() {
        assertThat(Arrays.stream(GameImportStatus.values()).filter(GameImportStatus::isTerminal))
                .containsExactlyInAnyOrder(CONFIRMED, FAILED);
    }

    @Test
    void rejectsANullTargetRatherThanTreatingItAsAState() {
        assertThatThrownBy(() -> UPLOADED.canTransitionTo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("next");
    }
}
