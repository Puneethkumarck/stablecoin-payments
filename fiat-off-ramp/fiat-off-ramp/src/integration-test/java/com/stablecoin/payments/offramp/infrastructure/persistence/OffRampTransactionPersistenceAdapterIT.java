package com.stablecoin.payments.offramp.infrastructure.persistence;

import com.stablecoin.payments.offramp.AbstractIntegrationTest;
import com.stablecoin.payments.offramp.domain.model.OffRampTransaction;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.port.OffRampTransactionRepository;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPendingOrder;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OffRampTransactionPersistenceAdapter IT")
class OffRampTransactionPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private OffRampTransactionRepository adapter;

    @Autowired
    private PayoutOrderRepository payoutOrderAdapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve transaction by id")
    void shouldSaveAndRetrieveById() {
        var order = savePayoutOrder();
        var txn = OffRampTransaction.create(
                order.payoutId(), "modulr", "payout.initiated",
                new BigDecimal("920.00"), "EUR", "processing",
                "{\"id\":\"mod_txn_001\",\"status\":\"processing\"}");
        var saved = adapter.save(txn);

        assertThat(adapter.findById(saved.offRampTxnId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .ignoringFields("rawResponse")
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find transactions by payout id")
    void shouldFindByPayoutId() {
        var order = savePayoutOrder();
        var txn = OffRampTransaction.create(
                order.payoutId(), "modulr", "payout.initiated",
                new BigDecimal("920.00"), "EUR", "processing", "{}");
        adapter.save(txn);

        assertThat(adapter.findByPayoutId(order.payoutId())).hasSize(1);
    }

    @Test
    @DisplayName("should return empty list for unknown payout id")
    void shouldReturnEmptyListForUnknownPayoutId() {
        assertThat(adapter.findByPayoutId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should return empty when id not found")
    void shouldReturnEmptyWhenIdNotFound() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    // ── Append-Only Pattern ──────────────────────────────────────────────

    @Test
    @DisplayName("should save multiple transactions for same payout")
    void shouldSaveMultipleTransactionsForSamePayout() {
        var order = savePayoutOrder();

        var txn1 = OffRampTransaction.create(
                order.payoutId(), "modulr", "payout.initiated",
                new BigDecimal("920.00"), "EUR", "processing", "{\"step\":1}");
        adapter.save(txn1);

        var txn2 = OffRampTransaction.create(
                order.payoutId(), "modulr", "payout.processing",
                new BigDecimal("920.00"), "EUR", "settling", "{\"step\":2}");
        adapter.save(txn2);

        var txn3 = OffRampTransaction.create(
                order.payoutId(), "modulr", "payout.completed",
                new BigDecimal("920.00"), "EUR", "completed", "{\"step\":3}");
        adapter.save(txn3);

        assertThat(adapter.findByPayoutId(order.payoutId())).hasSize(3);
    }

    // ── JSONB Round-Trip ─────────────────────────────────────────────────

    @Test
    @DisplayName("should persist raw response JSONB")
    void shouldPersistRawResponseJsonb() {
        var order = savePayoutOrder();
        var txn = OffRampTransaction.create(
                order.payoutId(), "modulr", "payout.completed",
                new BigDecimal("920.00"), "EUR", "completed",
                "{\"id\":\"mod_txn_999\",\"amount\":920.00,\"currency\":\"EUR\"}");
        adapter.save(txn);

        var results = adapter.findByPayoutId(order.payoutId());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().rawResponse()).contains("mod_txn_999");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private PayoutOrder savePayoutOrder() {
        return payoutOrderAdapter.save(aPendingOrder());
    }
}
