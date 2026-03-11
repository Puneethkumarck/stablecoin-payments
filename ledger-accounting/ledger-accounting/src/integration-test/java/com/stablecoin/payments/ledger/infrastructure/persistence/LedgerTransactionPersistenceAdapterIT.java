package com.stablecoin.payments.ledger.infrastructure.persistence;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.SOURCE_EVENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aBalancedTransaction;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LedgerTransactionPersistenceAdapter IT")
class LedgerTransactionPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private LedgerTransactionRepository adapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save transaction with entries and retrieve by id")
    void shouldSaveAndRetrieveById() {
        var saved = adapter.save(aBalancedTransaction());

        assertThat(adapter.findById(saved.transactionId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should retrieve transaction with correct number of entries")
    void shouldRetrieveTransactionWithCorrectEntries() {
        var saved = adapter.save(aBalancedTransaction());

        assertThat(adapter.findById(saved.transactionId()))
                .isPresent()
                .hasValueSatisfying(tx -> assertThat(tx.entries()).hasSize(2));
    }

    @Test
    @DisplayName("should find transactions by payment id ordered by created_at")
    void shouldFindByPaymentIdOrderedByCreatedAt() {
        adapter.save(aTransaction(PAYMENT_ID, UUID.randomUUID(), Instant.parse("2026-03-01T10:00:00Z")));
        adapter.save(aTransaction(PAYMENT_ID, UUID.randomUUID(), Instant.parse("2026-03-02T10:00:00Z")));

        var transactions = adapter.findByPaymentId(PAYMENT_ID);
        assertThat(transactions)
                .hasSize(2)
                .isSortedAccordingTo((a, b) -> a.createdAt().compareTo(b.createdAt()));
    }

    // ── Idempotency ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should return true for existing source event id")
    void shouldReturnTrueForExistingSourceEventId() {
        adapter.save(aBalancedTransaction());

        assertThat(adapter.existsBySourceEventId(SOURCE_EVENT_ID)).isTrue();
    }

    @Test
    @DisplayName("should return false for unknown source event id")
    void shouldReturnFalseForUnknownSourceEventId() {
        assertThat(adapter.existsBySourceEventId(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("should reject duplicate source event id")
    void shouldRejectDuplicateSourceEventId() {
        adapter.save(aBalancedTransaction());

        var duplicate = aTransaction(UUID.randomUUID(), SOURCE_EVENT_ID, Instant.now());

        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should return empty for non-existent transaction id")
    void shouldReturnEmptyForNonExistentTransactionId() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }
}
