package com.stablecoin.payments.ledger.infrastructure.persistence;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.port.JournalEntryRepository;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FIAT_RECEIVABLE;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aBalancedTransaction;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JournalEntryPersistenceAdapter IT")
class JournalEntryPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private JournalEntryRepository entryAdapter;

    @Autowired
    private LedgerTransactionRepository transactionAdapter;

    // ── Query by Payment ID ──────────────────────────────────────────────

    @Test
    @DisplayName("should find entries by payment id ordered by sequence number")
    void shouldFindByPaymentIdOrderedBySequenceNo() {
        transactionAdapter.save(aBalancedTransaction());

        var entries = entryAdapter.findByPaymentId(PAYMENT_ID);
        assertThat(entries)
                .hasSize(2)
                .isSortedAccordingTo((a, b) -> Integer.compare(a.sequenceNo(), b.sequenceNo()));
    }

    // ── Query by Transaction ID ──────────────────────────────────────────

    @Test
    @DisplayName("should find entries by transaction id")
    void shouldFindByTransactionId() {
        var saved = transactionAdapter.save(aBalancedTransaction());

        assertThat(entryAdapter.findByTransactionId(saved.transactionId())).hasSize(2);
    }

    // ── Query by Account Code & Currency ─────────────────────────────────

    @Test
    @DisplayName("should find entries by account code and currency")
    void shouldFindByAccountCodeAndCurrency() {
        transactionAdapter.save(aBalancedTransaction());

        assertThat(entryAdapter.findByAccountCodeAndCurrency(FIAT_RECEIVABLE, "USD")).hasSize(1);
    }

    // ── Count ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should count entries by payment id")
    void shouldCountByPaymentId() {
        transactionAdapter.save(aBalancedTransaction());

        assertThat(entryAdapter.countByPaymentId(PAYMENT_ID)).isEqualTo(2);
    }

    // ── Round-Trip Precision ─────────────────────────────────────────────

    @Test
    @DisplayName("should preserve BigDecimal precision through round-trip")
    void shouldPreserveBigDecimalPrecisionThroughRoundTrip() {
        var saved = transactionAdapter.save(aBalancedTransaction());

        var entries = entryAdapter.findByTransactionId(saved.transactionId());
        assertThat(entries)
                .isNotEmpty()
                .first()
                .satisfies(e -> assertThat(e.amount()).isEqualByComparingTo(new BigDecimal("10000.00")));
    }

    // ── Multiple Transactions ────────────────────────────────────────────

    @Test
    @DisplayName("should return entries from multiple transactions for same payment")
    void shouldReturnEntriesFromMultipleTransactions() {
        transactionAdapter.save(aBalancedTransaction());
        transactionAdapter.save(aTransaction(PAYMENT_ID, UUID.randomUUID()));

        assertThat(entryAdapter.findByPaymentId(PAYMENT_ID)).hasSize(4);
    }

    @Test
    @DisplayName("should return zero count for unknown payment id")
    void shouldReturnZeroCountForUnknownPaymentId() {
        assertThat(entryAdapter.countByPaymentId(UUID.randomUUID())).isZero();
    }
}
