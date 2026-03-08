package com.stablecoin.payments.onramp.infrastructure.persistence;

import com.stablecoin.payments.onramp.AbstractIntegrationTest;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.ERROR_CODE;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.FAILURE_REASON;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFERENCE;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aBankAccount;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectedMoney;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aMoney;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPaymentRail;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPendingOrder;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPspIdentifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CollectionOrderPersistenceAdapter IT")
class CollectionOrderPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private CollectionOrderRepository adapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve pending order")
    void shouldSaveAndRetrievePendingOrder() {
        var order = aPendingOrder();
        var saved = adapter.save(order);

        assertThat(adapter.findById(saved.collectionId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find by payment id")
    void shouldFindByPaymentId() {
        var order = aPendingOrder();
        var saved = adapter.save(order);

        assertThat(adapter.findByPaymentId(saved.paymentId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty when id not found")
    void shouldReturnEmptyWhenIdNotFound() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should return empty when payment id not found")
    void shouldReturnEmptyWhenPaymentIdNotFound() {
        assertThat(adapter.findByPaymentId(UUID.randomUUID())).isEmpty();
    }

    // ── Unique Constraints ───────────────────────────────────────────────

    @Test
    @DisplayName("should enforce unique payment id constraint")
    void shouldEnforceUniquePaymentIdConstraint() {
        var paymentId = UUID.randomUUID();
        var order1 = CollectionOrder.initiate(
                paymentId, UUID.randomUUID(), aMoney(), aPaymentRail(), aPspIdentifier(), aBankAccount());
        adapter.save(order1);

        var order2 = CollectionOrder.initiate(
                paymentId, UUID.randomUUID(), aMoney(), aPaymentRail(), aPspIdentifier(), aBankAccount());

        assertThatThrownBy(() -> adapter.save(order2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── State Machine Happy Path ─────────────────────────────────────────

    @Test
    @DisplayName("should update order through full happy path to COLLECTED")
    void shouldUpdateOrderThroughFullHappyPath() {
        var order = aPendingOrder();
        var saved = adapter.save(order);

        var initiated = saved.initiatePayment();
        var savedInitiated = adapter.save(initiated);

        var awaiting = savedInitiated.awaitConfirmation(PSP_REFERENCE);
        var savedAwaiting = adapter.save(awaiting);

        var collected = savedAwaiting.confirmCollection(aCollectedMoney());
        var expected = adapter.save(collected);

        assertThat(adapter.findById(order.collectionId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should update order through refund path")
    void shouldUpdateOrderThroughRefundPath() {
        var saved = adapter.save(aPendingOrder());
        saved = adapter.save(saved.initiatePayment());
        saved = adapter.save(saved.awaitConfirmation(PSP_REFERENCE));
        saved = adapter.save(saved.confirmCollection(aCollectedMoney()));
        saved = adapter.save(saved.initiateRefund());
        saved = adapter.save(saved.startRefundProcessing());
        var expected = adapter.save(saved.completeRefund());

        assertThat(adapter.findById(expected.collectionId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    // ── Failure Paths ────────────────────────────────────────────────────

    @Test
    @DisplayName("should update order through failure path")
    void shouldUpdateOrderThroughFailurePath() {
        var saved = adapter.save(aPendingOrder());
        var expected = adapter.save(saved.failCollection(FAILURE_REASON, ERROR_CODE));

        assertThat(adapter.findById(expected.collectionId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should persist amount mismatch and manual review")
    void shouldPersistAmountMismatchAndManualReview() {
        var saved = adapter.save(aPendingOrder());
        saved = adapter.save(saved.initiatePayment());
        saved = adapter.save(saved.awaitConfirmation(PSP_REFERENCE));
        saved = adapter.save(saved.detectAmountMismatch());
        var expected = adapter.save(saved.escalateToManualReview());

        assertThat(adapter.findById(expected.collectionId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    // ── Nullable Fields ──────────────────────────────────────────────────

    @Test
    @DisplayName("should persist nullable fields correctly for pending order")
    void shouldPersistNullableFieldsCorrectly() {
        var order = aPendingOrder();
        var saved = adapter.save(order);

        assertThat(adapter.findById(saved.collectionId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }
}
