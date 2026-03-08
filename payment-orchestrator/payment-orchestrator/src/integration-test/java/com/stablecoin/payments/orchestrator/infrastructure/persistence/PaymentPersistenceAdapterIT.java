package com.stablecoin.payments.orchestrator.infrastructure.persistence;

import com.stablecoin.payments.orchestrator.AbstractIntegrationTest;
import com.stablecoin.payments.orchestrator.domain.model.Money;
import com.stablecoin.payments.orchestrator.domain.model.Payment;
import com.stablecoin.payments.orchestrator.domain.model.PaymentState;
import com.stablecoin.payments.orchestrator.infrastructure.persistence.entity.PaymentJpaRepository;
import com.stablecoin.payments.orchestrator.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.CORRELATION_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.IDEMPOTENCY_KEY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.RECIPIENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SENDER_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_AMOUNT;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.TARGET_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.US_TO_DE;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aCompletedPayment;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.aValidFxRate;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anInitiatedPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentPersistenceAdapter IT")
class PaymentPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private PaymentPersistenceAdapter adapter;

    @Autowired
    private PaymentJpaRepository jpaRepository;

    @Autowired
    private PaymentPersistenceMapper mapper;

    @Autowired
    private EntityManager entityManager;

    // ── Save & Retrieve ─────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve payment with all value objects")
    void shouldSaveAndRetrievePaymentWithAllValueObjects() {
        var payment = aCompletedPayment();
        var saved = adapter.save(payment);

        assertThat(adapter.findById(saved.paymentId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should save initiated payment and retrieve by id")
    void shouldSaveInitiatedPaymentAndRetrieveById() {
        var payment = anInitiatedPayment();
        var saved = adapter.save(payment);

        assertThat(adapter.findById(saved.paymentId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty when payment id not found")
    void shouldReturnEmptyWhenPaymentIdNotFound() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    // ── findByIdempotencyKey ────────────────────────────────────────────

    @Test
    @DisplayName("should find payment by idempotency key")
    void shouldFindPaymentByIdempotencyKey() {
        var payment = anInitiatedPayment();
        var saved = adapter.save(payment);

        assertThat(adapter.findByIdempotencyKey(IDEMPOTENCY_KEY)).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty when idempotency key not found")
    void shouldReturnEmptyWhenIdempotencyKeyNotFound() {
        assertThat(adapter.findByIdempotencyKey("non-existent-key")).isEmpty();
    }

    // ── findBySenderIdAndState ──────────────────────────────────────────

    @Test
    @DisplayName("should find payments by sender id and state")
    void shouldFindPaymentsBySenderIdAndState() {
        var payment1 = Payment.initiate(
                "idem-sender-1", CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE);
        var payment2 = Payment.initiate(
                "idem-sender-2", CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE);
        adapter.save(payment1);
        adapter.save(payment2);

        var results = jpaRepository.findBySenderIdAndState(SENDER_ID, PaymentState.INITIATED);
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("should return empty list when no payments match sender and state")
    void shouldReturnEmptyListWhenNoPaymentsMatchSenderAndState() {
        var results = jpaRepository.findBySenderIdAndState(UUID.randomUUID(), PaymentState.COMPLETED);
        assertThat(results).isEmpty();
    }

    // ── JSONB metadata round-trip ───────────────────────────────────────

    @Test
    @DisplayName("should persist and retrieve JSONB metadata")
    void shouldPersistAndRetrieveJsonbMetadata() {
        var payment = anInitiatedPayment();
        var withMetadata = new Payment(
                payment.paymentId(),
                "idem-metadata-" + UUID.randomUUID(),
                payment.correlationId(),
                payment.state(),
                payment.senderId(),
                payment.recipientId(),
                payment.sourceAmount(),
                payment.sourceCurrency(),
                payment.targetCurrency(),
                payment.lockedFxRate(),
                payment.targetAmount(),
                payment.corridor(),
                payment.chainSelected(),
                payment.txHash(),
                payment.failureReason(),
                payment.createdAt(),
                payment.updatedAt(),
                payment.expiresAt(),
                Map.of("source", "api", "priority", "high", "merchantRef", "M-12345")
        );
        adapter.save(withMetadata);

        var found = adapter.findById(withMetadata.paymentId());
        assertThat(found).isPresent();
        assertThat(found.get().metadata()).containsEntry("source", "api");
        assertThat(found.get().metadata()).containsEntry("priority", "high");
        assertThat(found.get().metadata()).containsEntry("merchantRef", "M-12345");
    }

    @Test
    @DisplayName("should persist empty metadata as empty JSON object")
    void shouldPersistEmptyMetadataAsEmptyJsonObject() {
        var payment = anInitiatedPayment();
        var saved = adapter.save(payment);

        var found = adapter.findById(saved.paymentId());
        assertThat(found).isPresent();
        assertThat(found.get().metadata()).isEmpty();
    }

    // ── Optimistic locking ──────────────────────────────────────────────

    @Test
    @DisplayName("should detect optimistic locking conflict on concurrent update")
    void shouldDetectOptimisticLockingConflict() {
        var payment = anInitiatedPayment();
        adapter.save(payment);

        // Load entity and detach to simulate a separate session
        var entity1 = jpaRepository.findById(payment.paymentId()).orElseThrow();
        var staleVersion = entity1.getVersion();

        // First update via adapter increments the version
        var transitioned = payment.startComplianceCheck();
        adapter.save(transitioned);

        // Build a stale entity with the old version to simulate concurrent access
        var staleEntity = mapper.toEntity(payment);
        staleEntity.setVersion(staleVersion);
        staleEntity.setState(PaymentState.FAILED);
        staleEntity.setUpdatedAt(Instant.now());

        // Clear persistence context so Hibernate treats staleEntity as detached
        entityManager.clear();

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(staleEntity))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── Unique constraint ───────────────────────────────────────────────

    @Test
    @DisplayName("should enforce unique constraint on idempotency_key")
    void shouldEnforceUniqueConstraintOnIdempotencyKey() {
        var idempotencyKey = "unique-idem-" + UUID.randomUUID();
        var payment1 = Payment.initiate(
                idempotencyKey, CORRELATION_ID, SENDER_ID, RECIPIENT_ID,
                SOURCE_AMOUNT, SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE);
        adapter.save(payment1);

        var payment2 = Payment.initiate(
                idempotencyKey, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new Money(new BigDecimal("500.00"), "USD"), SOURCE_CURRENCY, TARGET_CURRENCY, US_TO_DE);

        assertThatThrownBy(() -> adapter.save(payment2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Update existing payment ─────────────────────────────────────────

    @Test
    @DisplayName("should update existing payment state transition")
    void shouldUpdateExistingPaymentStateTransition() {
        var payment = anInitiatedPayment();
        var saved = adapter.save(payment);

        // Transition to COMPLIANCE_CHECK
        var transitioned = saved.startComplianceCheck();
        var updated = adapter.save(transitioned);

        var expected = transitioned;
        assertThat(adapter.findById(updated.paymentId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should update payment with FX rate lock")
    void shouldUpdatePaymentWithFxRateLock() {
        var payment = anInitiatedPayment();
        adapter.save(payment);

        var complianceCheck = payment.startComplianceCheck();
        adapter.save(complianceCheck);

        var fxLocked = complianceCheck.lockFxRate(aValidFxRate());
        adapter.save(fxLocked);

        assertThat(adapter.findById(payment.paymentId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(fxLocked);
    }

    @Test
    @DisplayName("should update payment through full happy path to COMPLETED")
    void shouldUpdatePaymentThroughFullHappyPath() {
        var payment = aCompletedPayment();
        var saved = adapter.save(payment);

        assertThat(adapter.findById(saved.paymentId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
        assertThat(saved.state()).isEqualTo(PaymentState.COMPLETED);
    }
}
