package com.stablecoin.payments.offramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aStablecoinTicker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StablecoinRedemptionTest {

    private static final UUID PAYOUT_ID = UUID.fromString("e5f6a7b8-c9d0-1234-efab-567890123456");
    private static final BigDecimal REDEEMED_AMOUNT = new BigDecimal("500.00");
    private static final BigDecimal FIAT_RECEIVED = new BigDecimal("460.00");
    private static final String FIAT_CURRENCY = "EUR";
    private static final String PARTNER = "circle";
    private static final String PARTNER_REFERENCE = "circle_ref_abc123";

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates redemption with all required fields")
        void createsRedemptionWithAllFields() {
            var redemption = StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), REDEEMED_AMOUNT,
                    FIAT_RECEIVED, FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE);

            var expected = new Object() {
                final UUID payoutId = PAYOUT_ID;
                final BigDecimal redeemedAmount = REDEEMED_AMOUNT;
                final BigDecimal fiatReceived = FIAT_RECEIVED;
                final String fiatCurrency = FIAT_CURRENCY;
                final String partner = PARTNER;
                final String partnerReference = PARTNER_REFERENCE;
            };

            assertThat(redemption)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("payoutId", "redeemedAmount", "fiatReceived",
                            "fiatCurrency", "partner", "partnerReference")
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("generates a random redemptionId")
        void generatesRandomRedemptionId() {
            var redemption = StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), REDEEMED_AMOUNT,
                    FIAT_RECEIVED, FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE);

            assertThat(redemption.redemptionId()).isNotNull();
        }

        @Test
        @DisplayName("sets redeemedAt timestamp")
        void setsRedeemedAt() {
            var before = Instant.now();
            var redemption = StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), REDEEMED_AMOUNT,
                    FIAT_RECEIVED, FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE);

            assertThat(redemption.redeemedAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("populates stablecoin ticker with issuer and decimals")
        void populatesStablecoinTicker() {
            var redemption = StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), REDEEMED_AMOUNT,
                    FIAT_RECEIVED, FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE);

            assertThat(redemption.stablecoin().ticker()).isEqualTo("USDC");
            assertThat(redemption.stablecoin().issuer()).isEqualTo("circle");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("throws when payoutId is null")
        void throwsWhenPayoutIdNull() {
            assertThatThrownBy(() -> StablecoinRedemption.create(
                    null, aStablecoinTicker(), REDEEMED_AMOUNT,
                    FIAT_RECEIVED, FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("payoutId");
        }

        @Test
        @DisplayName("throws when stablecoin is null")
        void throwsWhenStablecoinNull() {
            assertThatThrownBy(() -> StablecoinRedemption.create(
                    PAYOUT_ID, null, REDEEMED_AMOUNT,
                    FIAT_RECEIVED, FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stablecoin");
        }

        @Test
        @DisplayName("throws when redeemedAmount is null")
        void throwsWhenRedeemedAmountNull() {
            assertThatThrownBy(() -> StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), null,
                    FIAT_RECEIVED, FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("redeemedAmount");
        }

        @Test
        @DisplayName("throws when redeemedAmount is zero")
        void throwsWhenRedeemedAmountZero() {
            assertThatThrownBy(() -> StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), BigDecimal.ZERO,
                    FIAT_RECEIVED, FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("redeemedAmount");
        }

        @Test
        @DisplayName("throws when fiatReceived is null")
        void throwsWhenFiatReceivedNull() {
            assertThatThrownBy(() -> StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), REDEEMED_AMOUNT,
                    null, FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fiatReceived");
        }

        @Test
        @DisplayName("throws when fiatReceived is negative")
        void throwsWhenFiatReceivedNegative() {
            assertThatThrownBy(() -> StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), REDEEMED_AMOUNT,
                    new BigDecimal("-1"), FIAT_CURRENCY, PARTNER, PARTNER_REFERENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fiatReceived");
        }

        @Test
        @DisplayName("throws when fiatCurrency is blank")
        void throwsWhenFiatCurrencyBlank() {
            assertThatThrownBy(() -> StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), REDEEMED_AMOUNT,
                    FIAT_RECEIVED, "  ", PARTNER, PARTNER_REFERENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fiatCurrency");
        }

        @Test
        @DisplayName("throws when partner is blank")
        void throwsWhenPartnerBlank() {
            assertThatThrownBy(() -> StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), REDEEMED_AMOUNT,
                    FIAT_RECEIVED, FIAT_CURRENCY, "", PARTNER_REFERENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("partner");
        }

        @Test
        @DisplayName("throws when partnerReference is null")
        void throwsWhenPartnerReferenceNull() {
            assertThatThrownBy(() -> StablecoinRedemption.create(
                    PAYOUT_ID, aStablecoinTicker(), REDEEMED_AMOUNT,
                    FIAT_RECEIVED, FIAT_CURRENCY, PARTNER, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("partnerReference");
        }
    }
}
