package com.chessapp.player.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlayerTest {

    private static final UUID ID = UUID.fromString("019535d9-3df7-79fb-b466-fa907fa17f9e");

    @Nested
    class Validation {

        @Test
        void storesTheTrimmedDisplayName() {
            assertThat(new NewPlayer("  Green, Guy  ", null, null).displayName())
                    .isEqualTo("Green, Guy");
        }

        @Test
        void rejectsANullDisplayName() {
            assertThatThrownBy(() -> new NewPlayer(null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("displayName");
        }

        @Test
        void rejectsABlankDisplayName() {
            assertThatThrownBy(() -> new NewPlayer("   ", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void rejectsThePgnUnknownMarker() {
            assertThatThrownBy(() -> new NewPlayer("?", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        void rejectsThePgnUnknownMarkerEvenWhenPadded() {
            assertThatThrownBy(() -> new NewPlayer("  ?  ", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        void acceptsAnAbsentFideIdAndFederation() {
            NewPlayer player = new NewPlayer("Club Opponent", null, null);

            assertThat(player.fideId()).isNull();
            assertThat(player.federation()).isNull();
        }

        @Test
        void acceptsANumericFideId() {
            assertThat(new NewPlayer("Carlsen, Magnus", "1503014", null).fideId())
                    .isEqualTo("1503014");
        }

        @Test
        void rejectsANonNumericFideId() {
            assertThatThrownBy(() -> new NewPlayer("Bad", "12a34", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fideId");
        }

        @Test
        void acceptsAThreeLetterUppercaseFederation() {
            assertThat(new NewPlayer("Green, Guy", null, "ENG").federation()).isEqualTo("ENG");
        }

        @Test
        void rejectsALowercaseFederation() {
            assertThatThrownBy(() -> new NewPlayer("Green, Guy", null, "eng"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("federation");
        }

        @Test
        void rejectsAFederationOfTheWrongLength() {
            assertThatThrownBy(() -> new NewPlayer("Green, Guy", null, "ENGL"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("federation");
        }
    }

    @Nested
    class PersistedPlayer {

        @Test
        void requiresAnId() {
            assertThatThrownBy(() -> new Player(null, "Green, Guy", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }

        @Test
        void appliesTheSameRulesWhenRehydratingAStoredRow() {
            assertThatThrownBy(() -> new Player(ID, "?", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        void holdsTheValidatedValues() {
            Player player = new Player(ID, "Carlsen, Magnus", "1503014", "NOR");

            assertThat(player.id()).isEqualTo(ID);
            assertThat(player.displayName()).isEqualTo("Carlsen, Magnus");
            assertThat(player.fideId()).isEqualTo("1503014");
            assertThat(player.federation()).isEqualTo("NOR");
        }
    }
}
