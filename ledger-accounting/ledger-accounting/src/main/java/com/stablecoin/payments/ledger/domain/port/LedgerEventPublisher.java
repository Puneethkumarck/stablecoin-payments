package com.stablecoin.payments.ledger.domain.port;

import com.stablecoin.payments.ledger.domain.event.ReconciliationCompletedDomainEvent;
import com.stablecoin.payments.ledger.domain.event.ReconciliationDiscrepancyDomainEvent;

public interface LedgerEventPublisher {

    void publishReconciliationCompleted(ReconciliationCompletedDomainEvent event);

    void publishReconciliationDiscrepancy(ReconciliationDiscrepancyDomainEvent event);
}
