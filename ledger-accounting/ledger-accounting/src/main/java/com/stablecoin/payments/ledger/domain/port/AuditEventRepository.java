package com.stablecoin.payments.ledger.domain.port;

import com.stablecoin.payments.ledger.domain.model.AuditEvent;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository {

    AuditEvent save(AuditEvent event);

    List<AuditEvent> findByPaymentId(UUID paymentId);

    List<AuditEvent> findByCorrelationId(UUID correlationId);
}
