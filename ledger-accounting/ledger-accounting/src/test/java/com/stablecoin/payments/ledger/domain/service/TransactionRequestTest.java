package com.stablecoin.payments.ledger.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.stablecoin.payments.ledger.domain.model.EntryType.CREDIT;
import static com.stablecoin.payments.ledger.domain.model.EntryType.DEBIT;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CORRELATION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.SOURCE_EVENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransactionRequest")
class TransactionRequestTest {

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("accepts valid request with 2 entries")
        void acceptsValidRequest() {
            var request = new TransactionRequest(
                    PAYMENT_ID, CORRELATION_ID, "payment.initiated", SOURCE_EVENT_ID,
                    "Test", List.of(
                    new JournalEntryRequest(DEBIT, "1000", new BigDecimal("100"), "USD"),
                    new JournalEntryRequest(CREDIT, "2010", new BigDecimal("100"), "USD")
            )
            );

            assertThat(request.entries()).hasSize(2);
        }

        @Test
        @DisplayName("rejects single entry")
        void rejectsSingleEntry() {
            assertThatThrownBy(() -> new TransactionRequest(
                    PAYMENT_ID, CORRELATION_ID, "test", SOURCE_EVENT_ID,
                    "Test", List.of(
                    new JournalEntryRequest(DEBIT, "1000", new BigDecimal("100"), "USD")
            )
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least 2 entries");
        }

        @Test
        @DisplayName("rejects empty entries")
        void rejectsEmptyEntries() {
            assertThatThrownBy(() -> new TransactionRequest(
                    PAYMENT_ID, CORRELATION_ID, "test", SOURCE_EVENT_ID,
                    "Test", List.of()
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least 2 entries");
        }

        @Test
        @DisplayName("rejects null paymentId")
        void rejectsNullPaymentId() {
            assertThatThrownBy(() -> new TransactionRequest(
                    null, CORRELATION_ID, "test", SOURCE_EVENT_ID,
                    "Test", List.of(
                    new JournalEntryRequest(DEBIT, "1000", new BigDecimal("100"), "USD"),
                    new JournalEntryRequest(CREDIT, "2010", new BigDecimal("100"), "USD")
            )
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("paymentId");
        }

        @Test
        @DisplayName("entries list is unmodifiable")
        void entriesListUnmodifiable() {
            var request = new TransactionRequest(
                    PAYMENT_ID, CORRELATION_ID, "test", SOURCE_EVENT_ID,
                    "Test", List.of(
                    new JournalEntryRequest(DEBIT, "1000", new BigDecimal("100"), "USD"),
                    new JournalEntryRequest(CREDIT, "2010", new BigDecimal("100"), "USD")
            )
            );

            assertThat(request.entries()).isUnmodifiable();
        }
    }
}
