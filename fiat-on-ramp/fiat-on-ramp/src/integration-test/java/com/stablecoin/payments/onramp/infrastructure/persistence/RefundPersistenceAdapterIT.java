package com.stablecoin.payments.onramp.infrastructure.persistence;

import com.stablecoin.payments.onramp.AbstractIntegrationTest;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.Refund;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.port.RefundRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFUND_REFERENCE;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPendingOrder;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.FAILURE_REASON;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.REFUND_REASON;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.aRefundAmount;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RefundPersistenceAdapter IT")
class RefundPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private RefundRepository adapter;

    @Autowired
    private CollectionOrderRepository collectionOrderAdapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve refund")
    void shouldSaveAndRetrieveRefund() {
        var order = saveCollectionOrder();
        var refund = Refund.initiate(order.collectionId(), order.paymentId(), aRefundAmount(), REFUND_REASON);
        var saved = adapter.save(refund);

        assertThat(adapter.findById(saved.refundId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find by collection id")
    void shouldFindByCollectionId() {
        var order = saveCollectionOrder();
        var refund1 = Refund.initiate(order.collectionId(), order.paymentId(), aRefundAmount(), REFUND_REASON);
        var saved1 = adapter.save(refund1);

        var refund2 = Refund.initiate(
                order.collectionId(), order.paymentId(),
                new Money(new BigDecimal("500.00"), "USD"), "Partial refund");
        var saved2 = adapter.save(refund2);

        var results = adapter.findByCollectionId(order.collectionId());
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("should return empty when refund id not found")
    void shouldReturnEmptyWhenRefundIdNotFound() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should return empty list when no refunds for collection")
    void shouldReturnEmptyListWhenNoRefundsForCollection() {
        assertThat(adapter.findByCollectionId(UUID.randomUUID())).isEmpty();
    }

    // ── State Transitions ────────────────────────────────────────────────

    @Test
    @DisplayName("should update refund through completion path")
    void shouldUpdateRefundThroughCompletionPath() {
        var order = saveCollectionOrder();
        var saved = adapter.save(
                Refund.initiate(order.collectionId(), order.paymentId(), aRefundAmount(), REFUND_REASON));
        saved = adapter.save(saved.startProcessing());
        var expected = adapter.save(saved.complete(PSP_REFUND_REFERENCE));

        assertThat(adapter.findById(expected.refundId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should update refund through failure path")
    void shouldUpdateRefundThroughFailurePath() {
        var order = saveCollectionOrder();
        var saved = adapter.save(
                Refund.initiate(order.collectionId(), order.paymentId(), aRefundAmount(), REFUND_REASON));
        saved = adapter.save(saved.startProcessing());
        var expected = adapter.save(saved.fail(FAILURE_REASON));

        assertThat(adapter.findById(expected.refundId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private CollectionOrder saveCollectionOrder() {
        return collectionOrderAdapter.save(aPendingOrder());
    }
}
