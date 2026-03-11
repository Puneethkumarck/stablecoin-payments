package com.stablecoin.payments.ledger.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.bhd;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.dai;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.eur;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.jpy;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.usd;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.usdc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Currency")
class CurrencyTest {

    @Nested
    @DisplayName("decimal precision")
    class DecimalPrecision {

        @Test
        @DisplayName("USD has 2 decimal places")
        void usdHasTwoDecimals() {
            assertThat(usd().decimalPrecision()).isEqualTo(2);
        }

        @Test
        @DisplayName("EUR has 2 decimal places")
        void eurHasTwoDecimals() {
            assertThat(eur().decimalPrecision()).isEqualTo(2);
        }

        @Test
        @DisplayName("USDC has 6 decimal places")
        void usdcHasSixDecimals() {
            assertThat(usdc().decimalPrecision()).isEqualTo(6);
        }

        @Test
        @DisplayName("DAI has 18 decimal places")
        void daiHasEighteenDecimals() {
            assertThat(dai().decimalPrecision()).isEqualTo(18);
        }

        @Test
        @DisplayName("JPY has 0 decimal places")
        void jpyHasZeroDecimals() {
            assertThat(jpy().decimalPrecision()).isEqualTo(0);
        }

        @Test
        @DisplayName("BHD has 3 decimal places")
        void bhdHasThreeDecimals() {
            assertThat(bhd().decimalPrecision()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects negative decimal precision")
        void rejectsNegativePrecision() {
            assertThatThrownBy(() -> new Currency("XXX", -1, true, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("decimalPrecision");
        }

        @Test
        @DisplayName("rejects precision above 18")
        void rejectsPrecisionAbove18() {
            assertThatThrownBy(() -> new Currency("XXX", 19, true, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("decimalPrecision");
        }

        @Test
        @DisplayName("rejects null code")
        void rejectsNullCode() {
            assertThatThrownBy(() -> new Currency(null, 2, true, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("code");
        }

        @Test
        @DisplayName("accepts precision of 0")
        void acceptsPrecisionZero() {
            var currency = new Currency("JPY", 0, true, null);
            assertThat(currency.decimalPrecision()).isEqualTo(0);
        }

        @Test
        @DisplayName("accepts precision of 18")
        void acceptsPrecisionEighteen() {
            var currency = new Currency("DAI", 18, true, null);
            assertThat(currency.decimalPrecision()).isEqualTo(18);
        }
    }
}
