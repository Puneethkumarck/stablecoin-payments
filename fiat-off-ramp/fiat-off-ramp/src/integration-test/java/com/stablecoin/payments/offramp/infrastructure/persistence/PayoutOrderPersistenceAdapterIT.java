package com.stablecoin.payments.offramp.infrastructure.persistence;

import com.stablecoin.payments.offramp.AbstractIntegrationTest;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.PayoutStatus;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.EXPECTED_FIAT_AMOUNT;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.FAILURE_REASON;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.PARTNER_REFERENCE;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aBankAccount;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPartnerIdentifier;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPendingHoldOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPendingMobileMoneyOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPendingOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aStablecoinTicker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PayoutOrderPersistenceAdapter IT")
class PayoutOrderPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private PayoutOrderRepository adapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve pending order with bank account")
    void shouldSaveAndRetrievePendingOrder() {
        var order = aPendingOrder();
        var saved = adapter.save(order);

        assertThat(adapter.findById(saved.payoutId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should save and retrieve pending order with mobile money account")
    void shouldSaveAndRetrievePendingMobileMoneyOrder() {
        var order = aPendingMobileMoneyOrder();
        var saved = adapter.save(order);

        assertThat(adapter.findById(saved.payoutId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find by payment id")
    void shouldFindByPaymentId() {
        var saved = adapter.save(aPendingOrder());

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

    // ── Find By Queries ─────────────────────────────────────────────────

    @Test
    @DisplayName("should find by status")
    void shouldFindByStatus() {
        adapter.save(aPendingOrder());

        var order2 = PayoutOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                com.stablecoin.payments.offramp.domain.model.PayoutType.FIAT,
                aStablecoinTicker(), new BigDecimal("500.00"), "USD",
                new BigDecimal("1.00"), UUID.randomUUID(), "hash_2",
                aBankAccount(), null,
                com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA, aPartnerIdentifier());
        adapter.save(order2);

        assertThat(adapter.findByStatus(PayoutStatus.PENDING)).hasSize(2);
    }

    @Test
    @DisplayName("should find by recipient id")
    void shouldFindByRecipientId() {
        var saved = adapter.save(aPendingOrder());

        assertThat(adapter.findByRecipientId(saved.recipientId())).hasSize(1);
        assertThat(adapter.findByRecipientId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should find by partner reference")
    void shouldFindByPartnerReference() {
        var saved = adapter.save(aPendingOrder());
        saved = adapter.save(saved.startRedemption());
        saved = adapter.save(saved.completeRedemption(EXPECTED_FIAT_AMOUNT));
        var expected = adapter.save(saved.initiatePayout(PARTNER_REFERENCE));

        assertThat(adapter.findByPartnerReference(PARTNER_REFERENCE)).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    // ── Unique Constraints ───────────────────────────────────────────────

    @Test
    @DisplayName("should enforce unique payment id constraint")
    void shouldEnforceUniquePaymentIdConstraint() {
        var paymentId = UUID.randomUUID();
        var order1 = PayoutOrder.create(
                paymentId, UUID.randomUUID(), UUID.randomUUID(),
                com.stablecoin.payments.offramp.domain.model.PayoutType.FIAT,
                aStablecoinTicker(), new BigDecimal("1000.00"), "EUR",
                new BigDecimal("0.92"), UUID.randomUUID(), "hash_1",
                aBankAccount(), null,
                com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA, aPartnerIdentifier());
        adapter.save(order1);

        var order2 = PayoutOrder.create(
                paymentId, UUID.randomUUID(), UUID.randomUUID(),
                com.stablecoin.payments.offramp.domain.model.PayoutType.FIAT,
                aStablecoinTicker(), new BigDecimal("500.00"), "EUR",
                new BigDecimal("0.92"), UUID.randomUUID(), "hash_2",
                aBankAccount(), null,
                com.stablecoin.payments.offramp.domain.model.PaymentRail.SEPA, aPartnerIdentifier());

        assertThatThrownBy(() -> adapter.save(order2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── State Machine Happy Path ─────────────────────────────────────────

    @Test
    @DisplayName("should update order through fiat happy path to COMPLETED")
    void shouldUpdateOrderThroughFiatHappyPath() {
        var saved = adapter.save(aPendingOrder());
        saved = adapter.save(saved.startRedemption());
        saved = adapter.save(saved.completeRedemption(EXPECTED_FIAT_AMOUNT));
        saved = adapter.save(saved.initiatePayout(PARTNER_REFERENCE));
        saved = adapter.save(saved.markPayoutProcessing());
        var expected = adapter.save(saved.completePayout(PARTNER_REFERENCE, Instant.now()));

        assertThat(adapter.findById(expected.payoutId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should update order through hold stablecoin path to COMPLETED")
    void shouldUpdateOrderThroughHoldPath() {
        var saved = adapter.save(aPendingHoldOrder());
        saved = adapter.save(saved.holdStablecoin());
        var expected = adapter.save(saved.completeHold());

        assertThat(adapter.findById(expected.payoutId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    // ── Failure Paths ────────────────────────────────────────────────────

    @Test
    @DisplayName("should update order through redemption failure to MANUAL_REVIEW")
    void shouldUpdateOrderThroughRedemptionFailure() {
        var saved = adapter.save(aPendingOrder());
        saved = adapter.save(saved.startRedemption());
        saved = adapter.save(saved.failRedemption(FAILURE_REASON));
        var expected = adapter.save(saved.escalateToManualReview());

        assertThat(adapter.findById(expected.payoutId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should update order through payout failure to MANUAL_REVIEW")
    void shouldUpdateOrderThroughPayoutFailure() {
        var saved = adapter.save(aPendingOrder());
        saved = adapter.save(saved.startRedemption());
        saved = adapter.save(saved.completeRedemption(EXPECTED_FIAT_AMOUNT));
        saved = adapter.save(saved.initiatePayout(PARTNER_REFERENCE));
        saved = adapter.save(saved.failPayout(FAILURE_REASON));
        var expected = adapter.save(saved.escalateToManualReview());

        assertThat(adapter.findById(expected.payoutId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }
}
