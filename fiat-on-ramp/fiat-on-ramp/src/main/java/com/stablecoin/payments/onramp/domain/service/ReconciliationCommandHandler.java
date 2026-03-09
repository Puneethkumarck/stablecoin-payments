package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.event.ReconciliationDiscrepancyEvent;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.ReconciliationRecord;
import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import com.stablecoin.payments.onramp.domain.port.ReconciliationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.stablecoin.payments.onramp.domain.model.ReconciliationStatus.DISCREPANCY;

/**
 * Domain service responsible for reconciling collection orders.
 * <p>
 * Compares expected vs collected amounts and persists the reconciliation result.
 * Publishes an alert event when a discrepancy is detected.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReconciliationCommandHandler {

    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final CollectionEventPublisher eventPublisher;

    /**
     * Reconciles a single collection order by comparing expected and collected amounts.
     *
     * @param order the collected order to reconcile
     * @return the persisted reconciliation record
     */
    public ReconciliationRecord reconcile(CollectionOrder order) {
        if (reconciliationRecordRepository.existsByCollectionId(order.collectionId())) {
            log.info("Reconciliation record already exists for collectionId={} — skipping",
                    order.collectionId());
            return reconciliationRecordRepository.findByCollectionId(order.collectionId())
                    .orElseThrow();
        }

        var actualAmount = order.collectedAmount() != null
                ? order.collectedAmount().amount()
                : null;

        var record = ReconciliationRecord.reconcile(
                order.collectionId(),
                order.psp() != null ? order.psp().pspName() : null,
                order.pspReference(),
                order.amount().amount(),
                actualAmount,
                order.amount().currency());

        var saved = reconciliationRecordRepository.save(record);

        if (saved.status() == DISCREPANCY) {
            log.warn("Reconciliation discrepancy detected collectionId={} expected={} actual={} diff={}",
                    order.collectionId(), saved.expectedAmount(), saved.actualAmount(),
                    saved.discrepancyAmount());

            eventPublisher.publish(new ReconciliationDiscrepancyEvent(
                    saved.reconciliationId(),
                    order.collectionId(),
                    order.paymentId(),
                    order.correlationId(),
                    saved.expectedAmount(),
                    saved.actualAmount(),
                    saved.discrepancyAmount(),
                    saved.currency(),
                    Instant.now()));
        }

        log.info("Reconciliation completed collectionId={} status={}",
                order.collectionId(), saved.status());

        return saved;
    }
}
