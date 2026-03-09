package com.stablecoin.payments.offramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OffRampTransactionTest {

    private static final UUID PAYOUT_ID = UUID.fromString("f6a7b8c9-d0e1-2345-fabc-678901234567");
    private static final String PARTNER_NAME = "Modulr";
    private static final String EVENT_TYPE = "PAYOUT_SUBMITTED";
    private static final BigDecimal AMOUNT = new BigDecimal("920.00");
    private static final String CURRENCY = "EUR";
    private static final String STATUS = "ACCEPTED";
    private static final String RAW_RESPONSE = "{\"txn_id\":\"mod_123\"}";

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates transaction with all required fields")
        void createsTransactionWithAllFields() {
            var txn = OffRampTransaction.create(
                    PAYOUT_ID, PARTNER_NAME, EVENT_TYPE,
                    AMOUNT, CURRENCY, STATUS, RAW_RESPONSE);

            var expected = new Object() {
                final UUID payoutId = PAYOUT_ID;
                final String partnerName = PARTNER_NAME;
                final String eventType = EVENT_TYPE;
                final BigDecimal amount = AMOUNT;
                final String currency = CURRENCY;
                final String status = STATUS;
                final String rawResponse = RAW_RESPONSE;
            };

            assertThat(txn)
                    .usingRecursiveComparison()
                    .comparingOnlyFields("payoutId", "partnerName", "eventType",
                            "amount", "currency", "status", "rawResponse")
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("generates a random offRampTxnId")
        void generatesRandomTxnId() {
            var txn = OffRampTransaction.create(
                    PAYOUT_ID, PARTNER_NAME, EVENT_TYPE,
                    AMOUNT, CURRENCY, STATUS, RAW_RESPONSE);

            assertThat(txn.offRampTxnId()).isNotNull();
        }

        @Test
        @DisplayName("sets receivedAt timestamp")
        void setsReceivedAt() {
            var before = Instant.now();
            var txn = OffRampTransaction.create(
                    PAYOUT_ID, PARTNER_NAME, EVENT_TYPE,
                    AMOUNT, CURRENCY, STATUS, RAW_RESPONSE);

            assertThat(txn.receivedAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("defaults rawResponse to empty JSON when null")
        void defaultsRawResponseToEmptyJson() {
            var txn = OffRampTransaction.create(
                    PAYOUT_ID, PARTNER_NAME, EVENT_TYPE,
                    AMOUNT, CURRENCY, STATUS, null);

            assertThat(txn.rawResponse()).isEqualTo("{}");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("throws when payoutId is null")
        void throwsWhenPayoutIdNull() {
            assertThatThrownBy(() -> OffRampTransaction.create(
                    null, PARTNER_NAME, EVENT_TYPE,
                    AMOUNT, CURRENCY, STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("payoutId");
        }

        @Test
        @DisplayName("throws when partnerName is blank")
        void throwsWhenPartnerNameBlank() {
            assertThatThrownBy(() -> OffRampTransaction.create(
                    PAYOUT_ID, "  ", EVENT_TYPE,
                    AMOUNT, CURRENCY, STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("partnerName");
        }

        @Test
        @DisplayName("throws when eventType is null")
        void throwsWhenEventTypeNull() {
            assertThatThrownBy(() -> OffRampTransaction.create(
                    PAYOUT_ID, PARTNER_NAME, null,
                    AMOUNT, CURRENCY, STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eventType");
        }

        @Test
        @DisplayName("throws when amount is null")
        void throwsWhenAmountNull() {
            assertThatThrownBy(() -> OffRampTransaction.create(
                    PAYOUT_ID, PARTNER_NAME, EVENT_TYPE,
                    null, CURRENCY, STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount");
        }

        @Test
        @DisplayName("throws when currency is blank")
        void throwsWhenCurrencyBlank() {
            assertThatThrownBy(() -> OffRampTransaction.create(
                    PAYOUT_ID, PARTNER_NAME, EVENT_TYPE,
                    AMOUNT, "", STATUS, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("currency");
        }

        @Test
        @DisplayName("throws when status is null")
        void throwsWhenStatusNull() {
            assertThatThrownBy(() -> OffRampTransaction.create(
                    PAYOUT_ID, PARTNER_NAME, EVENT_TYPE,
                    AMOUNT, CURRENCY, null, RAW_RESPONSE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("status");
        }
    }
}
