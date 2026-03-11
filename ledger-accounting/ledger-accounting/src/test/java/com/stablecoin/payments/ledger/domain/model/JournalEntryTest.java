package com.stablecoin.payments.ledger.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CORRELATION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.SOURCE_EVENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.TRANSACTION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aDebitEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JournalEntry")
class JournalEntryTest {

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("creates a valid debit entry")
        void createsValidDebitEntry() {
            var entry = aDebitEntry("1000", new BigDecimal("10000.00"), "USD");

            assertThat(entry.entryType()).isEqualTo(EntryType.DEBIT);
            assertThat(entry.amount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        }

        @Test
        @DisplayName("creates a valid credit entry")
        void createsValidCreditEntry() {
            var entry = new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, EntryType.CREDIT, "2010",
                    new BigDecimal("5000.00"), "USD",
                    new BigDecimal("5000.00"), 1L,
                    "fiat.collected", SOURCE_EVENT_ID, NOW
            );

            assertThat(entry.entryType()).isEqualTo(EntryType.CREDIT);
        }

        @Test
        @DisplayName("preserves sequence number")
        void preservesSequenceNumber() {
            var entry = new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    42, EntryType.DEBIT, "1000",
                    new BigDecimal("100.00"), "USD",
                    new BigDecimal("100.00"), 5L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            );

            assertThat(entry.sequenceNo()).isEqualTo(42);
        }

        @Test
        @DisplayName("preserves account version")
        void preservesAccountVersion() {
            var entry = new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, EntryType.DEBIT, "1000",
                    new BigDecimal("100.00"), "USD",
                    new BigDecimal("100.00"), 99L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            );

            assertThat(entry.accountVersion()).isEqualTo(99L);
        }

        @Test
        @DisplayName("preserves balance after snapshot")
        void preservesBalanceAfter() {
            var entry = new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, EntryType.DEBIT, "1000",
                    new BigDecimal("100.00"), "USD",
                    new BigDecimal("500.00"), 3L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            );

            assertThat(entry.balanceAfter()).isEqualByComparingTo(new BigDecimal("500.00"));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects zero amount")
        void rejectsZeroAmount() {
            assertThatThrownBy(() -> new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, EntryType.DEBIT, "1000",
                    BigDecimal.ZERO, "USD",
                    BigDecimal.ZERO, 1L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount must be positive");
        }

        @Test
        @DisplayName("rejects negative amount")
        void rejectsNegativeAmount() {
            assertThatThrownBy(() -> new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, EntryType.DEBIT, "1000",
                    new BigDecimal("-100.00"), "USD",
                    BigDecimal.ZERO, 1L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount must be positive");
        }

        @Test
        @DisplayName("rejects null entryId")
        void rejectsNullEntryId() {
            assertThatThrownBy(() -> new JournalEntry(
                    null, TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, EntryType.DEBIT, "1000",
                    new BigDecimal("100.00"), "USD",
                    new BigDecimal("100.00"), 1L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("entryId");
        }

        @Test
        @DisplayName("rejects null entryType")
        void rejectsNullEntryType() {
            assertThatThrownBy(() -> new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, null, "1000",
                    new BigDecimal("100.00"), "USD",
                    new BigDecimal("100.00"), 1L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("entryType");
        }

        @Test
        @DisplayName("rejects null accountCode")
        void rejectsNullAccountCode() {
            assertThatThrownBy(() -> new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, EntryType.DEBIT, null,
                    new BigDecimal("100.00"), "USD",
                    new BigDecimal("100.00"), 1L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("accountCode");
        }

        @Test
        @DisplayName("rejects null currency")
        void rejectsNullCurrency() {
            assertThatThrownBy(() -> new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, EntryType.DEBIT, "1000",
                    new BigDecimal("100.00"), null,
                    new BigDecimal("100.00"), 1L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("currency");
        }

        @Test
        @DisplayName("rejects null balanceAfter")
        void rejectsNullBalanceAfter() {
            assertThatThrownBy(() -> new JournalEntry(
                    UUID.randomUUID(), TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    1, EntryType.DEBIT, "1000",
                    new BigDecimal("100.00"), "USD",
                    null, 1L,
                    "payment.initiated", SOURCE_EVENT_ID, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("balanceAfter");
        }
    }
}
