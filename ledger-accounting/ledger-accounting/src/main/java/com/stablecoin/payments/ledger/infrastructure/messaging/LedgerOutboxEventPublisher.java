package com.stablecoin.payments.ledger.infrastructure.messaging;

import com.stablecoin.payments.ledger.api.events.ReconciliationCompletedEvent;
import com.stablecoin.payments.ledger.api.events.ReconciliationDiscrepancyEvent;
import com.stablecoin.payments.ledger.domain.event.ReconciliationCompletedDomainEvent;
import com.stablecoin.payments.ledger.domain.event.ReconciliationDiscrepancyDomainEvent;
import com.stablecoin.payments.ledger.domain.port.LedgerEventPublisher;
import io.namastack.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerOutboxEventPublisher implements LedgerEventPublisher {

    private final Outbox outbox;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishReconciliationCompleted(ReconciliationCompletedDomainEvent event) {
        var apiEvent = new ReconciliationCompletedEvent(
                ReconciliationCompletedEvent.SCHEMA_VERSION,
                UUID.randomUUID(),
                ReconciliationCompletedEvent.EVENT_TYPE,
                event.recId(),
                event.paymentId(),
                event.status().name(),
                event.completedAt());
        outbox.schedule(apiEvent, event.paymentId().toString());
        log.debug("Scheduled reconciliation.completed outbox event paymentId={}", event.paymentId());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishReconciliationDiscrepancy(ReconciliationDiscrepancyDomainEvent event) {
        var apiEvent = new ReconciliationDiscrepancyEvent(
                ReconciliationDiscrepancyEvent.SCHEMA_VERSION,
                UUID.randomUUID(),
                ReconciliationDiscrepancyEvent.EVENT_TYPE,
                event.recId(),
                event.paymentId(),
                event.discrepancy(),
                event.currency(),
                event.detail(),
                event.detectedAt());
        outbox.schedule(apiEvent, event.paymentId().toString());
        log.debug("Scheduled reconciliation.discrepancy outbox event paymentId={}", event.paymentId());
    }
}
