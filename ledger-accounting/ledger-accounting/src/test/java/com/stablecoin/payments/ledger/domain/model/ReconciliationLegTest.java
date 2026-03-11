package com.stablecoin.payments.ledger.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.FIAT_IN;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.REC_ID;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFiatInLeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReconciliationLeg")
class ReconciliationLegTest {

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("creates a valid leg")
        void createsValidLeg() {
            var leg = aFiatInLeg();

            assertThat(leg.legType()).isEqualTo(FIAT_IN);
            assertThat(leg.amount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        }

        @Test
        @DisplayName("amount is nullable (populated when event arrives)")
        void amountIsNullable() {
            var leg = new ReconciliationLeg(
                    UUID.randomUUID(), REC_ID, FIAT_IN,
                    null, null, UUID.randomUUID(), NOW
            );

            assertThat(leg.amount()).isNull();
        }

        @Test
        @DisplayName("currency is nullable")
        void currencyIsNullable() {
            var leg = new ReconciliationLeg(
                    UUID.randomUUID(), REC_ID, FIAT_IN,
                    new BigDecimal("100"), null, UUID.randomUUID(), NOW
            );

            assertThat(leg.currency()).isNull();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects null legId")
        void rejectsNullLegId() {
            assertThatThrownBy(() -> new ReconciliationLeg(
                    null, REC_ID, FIAT_IN,
                    new BigDecimal("100"), "USD", UUID.randomUUID(), NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("legId");
        }

        @Test
        @DisplayName("rejects null recId")
        void rejectsNullRecId() {
            assertThatThrownBy(() -> new ReconciliationLeg(
                    UUID.randomUUID(), null, FIAT_IN,
                    new BigDecimal("100"), "USD", UUID.randomUUID(), NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("recId");
        }

        @Test
        @DisplayName("rejects null legType")
        void rejectsNullLegType() {
            assertThatThrownBy(() -> new ReconciliationLeg(
                    UUID.randomUUID(), REC_ID, null,
                    new BigDecimal("100"), "USD", UUID.randomUUID(), NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("legType");
        }

        @Test
        @DisplayName("rejects null receivedAt")
        void rejectsNullReceivedAt() {
            assertThatThrownBy(() -> new ReconciliationLeg(
                    UUID.randomUUID(), REC_ID, FIAT_IN,
                    new BigDecimal("100"), "USD", UUID.randomUUID(), null
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("receivedAt");
        }
    }
}
