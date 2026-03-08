package com.stablecoin.payments.onramp.infrastructure.persistence;

import com.stablecoin.payments.onramp.AbstractIntegrationTest;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.PspTransaction;
import com.stablecoin.payments.onramp.domain.model.PspTransactionDirection;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.port.PspTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aMoney;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPendingOrder;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PspTransactionPersistenceAdapter IT")
class PspTransactionPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private PspTransactionRepository adapter;

    @Autowired
    private CollectionOrderRepository collectionOrderAdapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve by collection id")
    void shouldSaveAndRetrieveByCollectionId() {
        var order = saveCollectionOrder();
        var txn = PspTransaction.create(
                order.collectionId(), "Stripe", "pi_123", PspTransactionDirection.DEBIT,
                "payment_intent.succeeded", aMoney(), "succeeded", "{\"id\":\"pi_123\"}");
        adapter.save(txn);

        var results = adapter.findByCollectionId(order.collectionId());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst())
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .ignoringFields("rawResponse")
                .isEqualTo(txn);
    }

    @Test
    @DisplayName("should return empty list for unknown collection id")
    void shouldReturnEmptyListForUnknownCollectionId() {
        assertThat(adapter.findByCollectionId(UUID.randomUUID())).isEmpty();
    }

    // ── Append-Only Pattern ──────────────────────────────────────────────

    @Test
    @DisplayName("should save multiple transactions for same collection")
    void shouldSaveMultipleTransactionsForSameCollection() {
        var order = saveCollectionOrder();

        var txn1 = PspTransaction.create(
                order.collectionId(), "Stripe", "pi_123", PspTransactionDirection.DEBIT,
                "payment_intent.created", aMoney(), "requires_payment_method", "{\"status\":\"created\"}");
        adapter.save(txn1);

        var txn2 = PspTransaction.create(
                order.collectionId(), "Stripe", "pi_123", PspTransactionDirection.DEBIT,
                "payment_intent.succeeded", aMoney(), "succeeded", "{\"status\":\"succeeded\"}");
        adapter.save(txn2);

        var txn3 = PspTransaction.create(
                order.collectionId(), "Stripe", "re_456", PspTransactionDirection.CREDIT,
                "refund.created", aMoney(), "pending", "{\"status\":\"pending\"}");
        adapter.save(txn3);

        assertThat(adapter.findByCollectionId(order.collectionId())).hasSize(3);
    }

    // ── JSONB Round-Trip ─────────────────────────────────────────────────

    @Test
    @DisplayName("should persist raw response JSONB")
    void shouldPersistRawResponseJsonb() {
        var order = saveCollectionOrder();
        var txn = PspTransaction.create(
                order.collectionId(), "Stripe", "pi_789", PspTransactionDirection.DEBIT,
                "charge.succeeded", aMoney(), "succeeded",
                "{\"id\":\"ch_abc\",\"amount\":100000,\"currency\":\"usd\"}");
        adapter.save(txn);

        var results = adapter.findByCollectionId(order.collectionId());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().rawResponse()).contains("ch_abc");
    }

    // ── Enum Round-Trip ──────────────────────────────────────────────────

    @Test
    @DisplayName("should persist all direction enum values")
    void shouldPersistAllDirectionEnumValues() {
        var order = saveCollectionOrder();

        for (var direction : PspTransactionDirection.values()) {
            var txn = PspTransaction.create(
                    order.collectionId(), "Stripe", "ref_" + direction.name(),
                    direction, "event_" + direction.name(), aMoney(),
                    "completed", "{}");
            adapter.save(txn);
        }

        assertThat(adapter.findByCollectionId(order.collectionId()))
                .hasSize(PspTransactionDirection.values().length);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private CollectionOrder saveCollectionOrder() {
        return collectionOrderAdapter.save(aPendingOrder());
    }
}
