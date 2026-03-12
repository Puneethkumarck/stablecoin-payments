package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.event.ReconciliationCompletedDomainEvent;
import com.stablecoin.payments.ledger.domain.event.ReconciliationDiscrepancyDomainEvent;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.model.ReconciliationStatus;
import com.stablecoin.payments.ledger.domain.port.LedgerEventPublisher;
import com.stablecoin.payments.ledger.domain.port.ReconciliationLegRepository;
import com.stablecoin.payments.ledger.domain.port.ReconciliationProperties;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Reconciliation command handler: records legs as payment lifecycle events arrive,
 * finalizes reconciliation when all 5 required legs are present, and publishes
 * outcome events via outbox.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReconciliationCommandHandler {

    private final ReconciliationRepository reconciliationRepository;
    private final ReconciliationLegRepository legRepository;
    private final ReconciliationProperties properties;
    private final LedgerEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Creates a PENDING reconciliation record for a payment.
     * Idempotent: returns existing record if already created.
     */
    public ReconciliationRecord createRecord(UUID paymentId) {
        return reconciliationRepository.findByPaymentId(paymentId)
                .orElseGet(() -> {
                    var record = ReconciliationRecord.create(paymentId, properties.tolerance());
                    return reconciliationRepository.save(record);
                });
    }

    /**
     * Records a reconciliation leg for a payment. Creates the reconciliation
     * record if it doesn't exist. Idempotent: skips if leg type already recorded.
     */
    public void recordLeg(UUID paymentId, ReconciliationLegType legType,
                          BigDecimal amount, String currency, UUID sourceEventId) {
        var record = createRecord(paymentId);

        if (record.hasLeg(legType)) {
            log.info("[RECONCILIATION] Leg {} already recorded for paymentId={}", legType, paymentId);
            return;
        }

        var leg = new ReconciliationLeg(
                UUID.randomUUID(), record.recId(), legType,
                amount, currency, sourceEventId, clock.instant());
        legRepository.save(leg);
        reconciliationRepository.save(record.addLeg(leg));
    }

    /**
     * Finds a specific reconciliation leg for a payment.
     */
    public Optional<ReconciliationLeg> findLeg(UUID paymentId, ReconciliationLegType legType) {
        return reconciliationRepository.findByPaymentId(paymentId)
                .flatMap(record -> record.legs().stream()
                        .filter(l -> l.legType() == legType)
                        .findFirst());
    }

    /**
     * Marks reconciliation as DISCREPANCY (e.g., on payment failure).
     */
    public void markDiscrepancy(UUID paymentId) {
        reconciliationRepository.findByPaymentId(paymentId)
                .ifPresent(record -> {
                    var saved = reconciliationRepository.save(record.markDiscrepancy());
                    var discrepancy = calculateDiscrepancy(record);
                    var currency = resolveCurrency(record);
                    eventPublisher.publishReconciliationDiscrepancy(
                            new ReconciliationDiscrepancyDomainEvent(
                                    saved.recId(), saved.paymentId(), discrepancy, currency,
                                    "Payment failed — marked as discrepancy", clock.instant()));
                    log.info("[RECONCILIATION] Marked DISCREPANCY for paymentId={}", paymentId);
                });
    }

    /**
     * Finalizes reconciliation if all 5 required legs are present.
     * Calculates discrepancy (|stablecoin_minted - stablecoin_redeemed|) and
     * compares against tolerance. Publishes RECONCILED or DISCREPANCY event.
     * Idempotent: skips already finalized records or records without all legs.
     */
    public Optional<ReconciliationRecord> finalizeReconciliation(UUID paymentId) {
        return reconciliationRepository.findByPaymentId(paymentId)
                .filter(r -> r.status() != ReconciliationStatus.RECONCILED
                        && r.status() != ReconciliationStatus.DISCREPANCY)
                .filter(ReconciliationRecord::hasAllRequiredLegs)
                .map(record -> {
                    var discrepancy = calculateDiscrepancy(record);
                    var finalized = record.finalize(discrepancy);
                    var saved = reconciliationRepository.save(finalized);
                    publishOutcome(saved, discrepancy);
                    return saved;
                });
    }

    private void publishOutcome(ReconciliationRecord saved, BigDecimal discrepancy) {
        if (saved.status() == ReconciliationStatus.RECONCILED) {
            eventPublisher.publishReconciliationCompleted(
                    new ReconciliationCompletedDomainEvent(
                            saved.recId(), saved.paymentId(), saved.status(), clock.instant()));
            log.info("[RECONCILIATION] RECONCILED paymentId={}", saved.paymentId());
        } else {
            var currency = resolveCurrency(saved);
            eventPublisher.publishReconciliationDiscrepancy(
                    new ReconciliationDiscrepancyDomainEvent(
                            saved.recId(), saved.paymentId(), discrepancy, currency,
                            "Stablecoin minted/redeemed discrepancy exceeds tolerance",
                            clock.instant()));
            log.info("[RECONCILIATION] DISCREPANCY paymentId={} amount={}",
                    saved.paymentId(), discrepancy);
        }
    }

    /**
     * Discrepancy = |stablecoin_minted - stablecoin_redeemed|.
     * These should be identical in a normal stablecoin sandwich flow.
     */
    BigDecimal calculateDiscrepancy(ReconciliationRecord record) {
        var mintedAmount = record.legs().stream()
                .filter(l -> l.legType() == ReconciliationLegType.STABLECOIN_MINTED)
                .map(ReconciliationLeg::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        var redeemedAmount = record.legs().stream()
                .filter(l -> l.legType() == ReconciliationLegType.STABLECOIN_REDEEMED)
                .map(ReconciliationLeg::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        return mintedAmount.subtract(redeemedAmount).abs();
    }

    private String resolveCurrency(ReconciliationRecord record) {
        return record.legs().stream()
                .filter(l -> l.legType() == ReconciliationLegType.STABLECOIN_MINTED)
                .map(ReconciliationLeg::currency)
                .findFirst()
                .orElse("UNKNOWN");
    }
}
