package com.stablecoin.payments.ledger.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CORRELATION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.SOURCE_EVENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.TRANSACTION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aBalancedEntryPair;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aBalancedTransaction;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aCreditEntry;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aDebitEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LedgerTransaction")
class LedgerTransactionTest {

    @Nested
    @DisplayName("creation with balanced entries")
    class BalancedEntries {

        @Test
        @DisplayName("creates transaction when debits equal credits")
        void createsBalancedTransaction() {
            var transaction = aBalancedTransaction();

            assertThat(transaction.transactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(transaction.entries()).hasSize(2);
        }

        @Test
        @DisplayName("total debits equals total credits")
        void totalDebitsEqualsCredits() {
            var transaction = aBalancedTransaction();

            assertThat(transaction.totalDebits()).isEqualByComparingTo(transaction.totalCredits());
        }

        @Test
        @DisplayName("computes total debits correctly")
        void computesTotalDebits() {
            var transaction = aBalancedTransaction();

            assertThat(transaction.totalDebits()).isEqualByComparingTo(new BigDecimal("10000.00"));
        }

        @Test
        @DisplayName("computes total credits correctly")
        void computesTotalCredits() {
            var transaction = aBalancedTransaction();

            assertThat(transaction.totalCredits()).isEqualByComparingTo(new BigDecimal("10000.00"));
        }

        @Test
        @DisplayName("accepts multiple entry pairs that balance")
        void acceptsMultipleBalancedPairs() {
            var entries = List.of(
                    aDebitEntry("1000", new BigDecimal("5000.00"), "USD"),
                    aDebitEntry("1001", new BigDecimal("5000.00"), "USD"),
                    aCreditEntry("2010", new BigDecimal("10000.00"), "USD")
            );

            var transaction = new LedgerTransaction(
                    TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    "payment.initiated", SOURCE_EVENT_ID,
                    "Multi-entry transaction", entries, NOW
            );

            assertThat(transaction.entries()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("validation — rejects invalid transactions")
    class Validation {

        @Test
        @DisplayName("rejects unbalanced transaction")
        void rejectsUnbalancedTransaction() {
            var entries = List.of(
                    aDebitEntry("1000", new BigDecimal("10000.00"), "USD"),
                    aCreditEntry("2010", new BigDecimal("9000.00"), "USD")
            );

            assertThatThrownBy(() -> new LedgerTransaction(
                    TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    "payment.initiated", SOURCE_EVENT_ID,
                    "Unbalanced", entries, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not balanced");
        }

        @Test
        @DisplayName("rejects single entry")
        void rejectsSingleEntry() {
            var entries = List.of(
                    aDebitEntry("1000", new BigDecimal("10000.00"), "USD")
            );

            assertThatThrownBy(() -> new LedgerTransaction(
                    TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    "payment.initiated", SOURCE_EVENT_ID,
                    "Single entry", entries, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 2 entries");
        }

        @Test
        @DisplayName("rejects empty entries")
        void rejectsEmptyEntries() {
            assertThatThrownBy(() -> new LedgerTransaction(
                    TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    "payment.initiated", SOURCE_EVENT_ID,
                    "Empty", List.of(), NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 2 entries");
        }

        @Test
        @DisplayName("rejects cross-currency entries that appear balanced by amount")
        void rejectsCrossCurrencyEntries() {
            var entries = List.of(
                    aDebitEntry("1000", new BigDecimal("10000.00"), "USD"),
                    aCreditEntry("2010", new BigDecimal("10000.00"), "EUR")
            );

            assertThatThrownBy(() -> new LedgerTransaction(
                    TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    "payment.initiated", SOURCE_EVENT_ID,
                    "Cross-currency", entries, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not balanced");
        }

        @Test
        @DisplayName("rejects null transactionId")
        void rejectsNullTransactionId() {
            assertThatThrownBy(() -> new LedgerTransaction(
                    null, PAYMENT_ID, CORRELATION_ID,
                    "payment.initiated", SOURCE_EVENT_ID,
                    "Test", aBalancedEntryPair(new BigDecimal("100"), "USD"), NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("transactionId");
        }

        @Test
        @DisplayName("rejects null paymentId")
        void rejectsNullPaymentId() {
            assertThatThrownBy(() -> new LedgerTransaction(
                    TRANSACTION_ID, null, CORRELATION_ID,
                    "payment.initiated", SOURCE_EVENT_ID,
                    "Test", aBalancedEntryPair(new BigDecimal("100"), "USD"), NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("paymentId");
        }

        @Test
        @DisplayName("rejects null sourceEvent")
        void rejectsNullSourceEvent() {
            assertThatThrownBy(() -> new LedgerTransaction(
                    TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    null, SOURCE_EVENT_ID,
                    "Test", aBalancedEntryPair(new BigDecimal("100"), "USD"), NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sourceEvent");
        }

        @Test
        @DisplayName("rejects null entries list")
        void rejectsNullEntries() {
            assertThatThrownBy(() -> new LedgerTransaction(
                    TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    "payment.initiated", SOURCE_EVENT_ID,
                    "Test", null, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("entries");
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("entries list is unmodifiable")
        void entriesListIsUnmodifiable() {
            var transaction = aBalancedTransaction();

            assertThatThrownBy(() -> transaction.entries().add(
                    aDebitEntry("1000", new BigDecimal("100"), "USD")
            )).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
