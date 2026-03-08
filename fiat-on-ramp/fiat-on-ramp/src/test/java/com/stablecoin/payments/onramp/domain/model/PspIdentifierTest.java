package com.stablecoin.payments.onramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPspIdentifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PspIdentifier")
class PspIdentifierTest {

    @Nested
    @DisplayName("Valid Construction")
    class ValidConstruction {

        @Test
        @DisplayName("creates PspIdentifier with all required fields")
        void createsPspIdentifierWithAllFields() {
            var psp = new PspIdentifier("stripe_001", "Stripe");

            var expected = new PspIdentifier("stripe_001", "Stripe");

            assertThat(psp)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("fixture creates valid PspIdentifier")
        void fixtureCreatesValidPspIdentifier() {
            var psp = aPspIdentifier();

            assertThat(psp.pspId()).isNotBlank();
            assertThat(psp.pspName()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Invalid Construction")
    class InvalidConstruction {

        @Test
        @DisplayName("rejects null pspId")
        void rejectsNullPspId() {
            assertThatThrownBy(() -> new PspIdentifier(null, "Stripe"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PSP ID is required");
        }

        @Test
        @DisplayName("rejects blank pspId")
        void rejectsBlankPspId() {
            assertThatThrownBy(() -> new PspIdentifier("   ", "Stripe"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PSP ID is required");
        }

        @Test
        @DisplayName("rejects empty pspId")
        void rejectsEmptyPspId() {
            assertThatThrownBy(() -> new PspIdentifier("", "Stripe"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PSP ID is required");
        }

        @Test
        @DisplayName("rejects null pspName")
        void rejectsNullPspName() {
            assertThatThrownBy(() -> new PspIdentifier("stripe_001", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PSP name is required");
        }

        @Test
        @DisplayName("rejects blank pspName")
        void rejectsBlankPspName() {
            assertThatThrownBy(() -> new PspIdentifier("stripe_001", "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PSP name is required");
        }

        @Test
        @DisplayName("rejects empty pspName")
        void rejectsEmptyPspName() {
            assertThatThrownBy(() -> new PspIdentifier("stripe_001", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PSP name is required");
        }
    }
}
