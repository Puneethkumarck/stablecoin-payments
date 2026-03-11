package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
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
 * Minimal reconciliation command handler for recording reconciliation legs
 * as payment lifecycle events arrive. Full reconciliation matching logic
 * (finalize, discrepancy detection) is deferred to STA-165.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReconciliationCommandHandler {

    private final ReconciliationRepository reconciliationRepository;
    private final ReconciliationLegRepository legRepository;
    private final ReconciliationProperties properties;
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
                    reconciliationRepository.save(record.markDiscrepancy());
                    log.info("[RECONCILIATION] Marked DISCREPANCY for paymentId={}", paymentId);
                });
    }
}
