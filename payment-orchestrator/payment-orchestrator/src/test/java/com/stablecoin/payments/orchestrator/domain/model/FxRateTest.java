package com.stablecoin.payments.orchestrator.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FxRate value object")
class FxRateTest {

    private static final UUID QUOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final Instant LOCKED_AT = Instant.parse("2026-03-08T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-03-08T10:10:00Z");

    private FxRate validFxRate() {
        return new FxRate(QUOTE_ID, "USD", "EUR", new BigDecimal("0.92"), LOCKED_AT, EXPIRES_AT, "ecb");
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("creates valid FxRate with all fields")
        void createsValidFxRate() {
            var fxRate = validFxRate();

            var expected = new FxRate(QUOTE_ID, "USD", "EUR", new BigDecimal("0.92"), LOCKED_AT, EXPIRES_AT, "ecb");

            assertThat(fxRate)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("rejects null quoteId")
        void rejectsNullQuoteId() {
            assertThatThrownBy(() -> new FxRate(null, "USD", "EUR", new BigDecimal("0.92"), LOCKED_AT, EXPIRES_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("quoteId is required");
        }

        @Test
        @DisplayName("rejects null from currency")
        void rejectsNullFrom() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, null, "EUR", new BigDecimal("0.92"), LOCKED_AT, EXPIRES_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("from currency is required");
        }

        @Test
        @DisplayName("rejects blank from currency")
        void rejectsBlankFrom() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "  ", "EUR", new BigDecimal("0.92"), LOCKED_AT, EXPIRES_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("from currency is required");
        }

        @Test
        @DisplayName("rejects null to currency")
        void rejectsNullTo() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", null, new BigDecimal("0.92"), LOCKED_AT, EXPIRES_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("to currency is required");
        }

        @Test
        @DisplayName("rejects blank to currency")
        void rejectsBlankTo() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "  ", new BigDecimal("0.92"), LOCKED_AT, EXPIRES_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("to currency is required");
        }

        @Test
        @DisplayName("rejects null rate")
        void rejectsNullRate() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "EUR", null, LOCKED_AT, EXPIRES_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Rate must be positive");
        }

        @Test
        @DisplayName("rejects zero rate")
        void rejectsZeroRate() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "EUR", BigDecimal.ZERO, LOCKED_AT, EXPIRES_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Rate must be positive");
        }

        @Test
        @DisplayName("rejects negative rate")
        void rejectsNegativeRate() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "EUR", new BigDecimal("-0.92"), LOCKED_AT, EXPIRES_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Rate must be positive");
        }

        @Test
        @DisplayName("rejects null lockedAt")
        void rejectsNullLockedAt() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "EUR", new BigDecimal("0.92"), null, EXPIRES_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("lockedAt is required");
        }

        @Test
        @DisplayName("rejects null expiresAt")
        void rejectsNullExpiresAt() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "EUR", new BigDecimal("0.92"), LOCKED_AT, null, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("expiresAt is required");
        }

        @Test
        @DisplayName("rejects expiresAt equal to lockedAt")
        void rejectsExpiresAtEqualToLockedAt() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "EUR", new BigDecimal("0.92"), LOCKED_AT, LOCKED_AT, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("expiresAt must be after lockedAt");
        }

        @Test
        @DisplayName("rejects expiresAt before lockedAt")
        void rejectsExpiresAtBeforeLockedAt() {
            var beforeLockedAt = LOCKED_AT.minusSeconds(60);
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "EUR", new BigDecimal("0.92"), LOCKED_AT, beforeLockedAt, "ecb"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("expiresAt must be after lockedAt");
        }

        @Test
        @DisplayName("rejects null provider")
        void rejectsNullProvider() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "EUR", new BigDecimal("0.92"), LOCKED_AT, EXPIRES_AT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("provider is required");
        }

        @Test
        @DisplayName("rejects blank provider")
        void rejectsBlankProvider() {
            assertThatThrownBy(() -> new FxRate(QUOTE_ID, "USD", "EUR", new BigDecimal("0.92"), LOCKED_AT, EXPIRES_AT, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("provider is required");
        }
    }

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("returns false when checked before expiresAt")
        void notExpiredBeforeExpiry() {
            var fxRate = validFxRate();

            assertThat(fxRate.isExpired(EXPIRES_AT.minusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("returns false when checked at exactly expiresAt")
        void notExpiredAtExactExpiry() {
            var fxRate = validFxRate();

            assertThat(fxRate.isExpired(EXPIRES_AT)).isFalse();
        }

        @Test
        @DisplayName("returns true when checked after expiresAt")
        void expiredAfterExpiry() {
            var fxRate = validFxRate();

            assertThat(fxRate.isExpired(EXPIRES_AT.plusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("returns false when checked at lockedAt")
        void notExpiredAtLockedAt() {
            var fxRate = validFxRate();

            assertThat(fxRate.isExpired(LOCKED_AT)).isFalse();
        }
    }
}
