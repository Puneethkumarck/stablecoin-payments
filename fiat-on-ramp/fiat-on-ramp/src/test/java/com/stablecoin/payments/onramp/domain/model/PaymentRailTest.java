package com.stablecoin.payments.onramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.stablecoin.payments.onramp.domain.model.PaymentRailType.ACH;
import static com.stablecoin.payments.onramp.domain.model.PaymentRailType.SEPA;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPaymentRail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentRail")
class PaymentRailTest {

    @Nested
    @DisplayName("Valid Construction")
    class ValidConstruction {

        @Test
        @DisplayName("creates PaymentRail with all required fields")
        void createsPaymentRailWithAllFields() {
            var rail = new PaymentRail(SEPA, "DE", "EUR");

            var expected = new PaymentRail(SEPA, "DE", "EUR");

            assertThat(rail)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("creates PaymentRail with ACH type")
        void createsPaymentRailWithAchType() {
            var rail = new PaymentRail(ACH, "US", "USD");

            assertThat(rail.rail()).isEqualTo(ACH);
        }

        @Test
        @DisplayName("fixture creates valid PaymentRail")
        void fixtureCreatesValidPaymentRail() {
            var rail = aPaymentRail();

            assertThat(rail.rail()).isNotNull();
            assertThat(rail.country()).isNotBlank();
            assertThat(rail.currency()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Invalid Construction")
    class InvalidConstruction {

        @Test
        @DisplayName("rejects null rail")
        void rejectsNullRail() {
            assertThatThrownBy(() -> new PaymentRail(null, "DE", "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Rail is required");
        }

        @Test
        @DisplayName("rejects null country")
        void rejectsNullCountry() {
            assertThatThrownBy(() -> new PaymentRail(SEPA, null, "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Country is required");
        }

        @Test
        @DisplayName("rejects blank country")
        void rejectsBlankCountry() {
            assertThatThrownBy(() -> new PaymentRail(SEPA, "   ", "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Country is required");
        }

        @Test
        @DisplayName("rejects null currency")
        void rejectsNullCurrency() {
            assertThatThrownBy(() -> new PaymentRail(SEPA, "DE", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Currency is required");
        }

        @Test
        @DisplayName("rejects blank currency")
        void rejectsBlankCurrency() {
            assertThatThrownBy(() -> new PaymentRail(SEPA, "DE", "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Currency is required");
        }
    }
}
