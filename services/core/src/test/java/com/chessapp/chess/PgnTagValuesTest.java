package com.chessapp.chess;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PgnTagValuesTest {

    @Nested
    class Optional {

        @Test
        void trimsAValueThatIsPresent() {
            assertThat(PgnTagValues.optional("  Club Championship  ")).isEqualTo("Club Championship");
        }

        @Test
        void treatsAbsentBlankAndTheUnknownMarkerAsTheSameThing() {
            assertThat(PgnTagValues.optional(null)).isNull();
            assertThat(PgnTagValues.optional("   ")).isNull();
            assertThat(PgnTagValues.optional("?")).isNull();
        }
    }

    @Nested
    class Date {

        @Test
        void readsAFullyKnownDate() {
            assertThat(PgnTagValues.date("2026.03.14")).isEqualTo(LocalDate.of(2026, 3, 14));
        }

        @Test
        void returnsNullForAWhollyUnknownDate() {
            assertThat(PgnTagValues.date("????.??.??")).isNull();
        }

        @Test
        void returnsNullForAPartiallyKnownDateBecauseTheColumnStoresNoPrecision() {
            assertThat(PgnTagValues.date("2026.??.??")).isNull();
            assertThat(PgnTagValues.date("2026.03.??")).isNull();
        }

        @Test
        void returnsNullForADateThatCannotExist() {
            assertThat(PgnTagValues.date("2026.02.30")).isNull();
            assertThat(PgnTagValues.date("2026.13.01")).isNull();
        }

        @Test
        void returnsNullForAbsentOrMalformedInput() {
            assertThat(PgnTagValues.date(null)).isNull();
            assertThat(PgnTagValues.date("14 March 2026")).isNull();
        }
    }

    @Nested
    class Rating {

        @Test
        void readsANumericRating() {
            assertThat(PgnTagValues.rating("1850")).isEqualTo(1850);
        }

        @Test
        void returnsNullRatherThanRejectingAnUnusableRating() {
            assertThat(PgnTagValues.rating(null)).isNull();
            assertThat(PgnTagValues.rating("?")).isNull();
            assertThat(PgnTagValues.rating("   ")).isNull();
            assertThat(PgnTagValues.rating("unrated")).isNull();
            assertThat(PgnTagValues.rating("0")).isNull();
            assertThat(PgnTagValues.rating("-100")).isNull();
        }

        @Test
        void returnsNullForANumberTooLargeToBeARating() {
            assertThat(PgnTagValues.rating("99999999999999999999")).isNull();
        }
    }

    @Nested
    class Eco {

        @Test
        void readsAWellFormedCode() {
            assertThat(PgnTagValues.eco("C60")).isEqualTo("C60");
        }

        @Test
        void returnsNullRatherThanRejectingAMalformedCode() {
            assertThat(PgnTagValues.eco("F60")).isNull();
            assertThat(PgnTagValues.eco("C6")).isNull();
            assertThat(PgnTagValues.eco("?")).isNull();
            assertThat(PgnTagValues.eco(null)).isNull();
        }
    }
}
