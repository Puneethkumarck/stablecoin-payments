package com.stablecoin.payments.ledger.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only audit event for the ledger service.
 * Immutable once created — no updates or deletes at domain or DB level.
 */
public record AuditEvent(
        UUID auditId,
        UUID correlationId,
        UUID paymentId,
        String serviceName,
        String eventType,
        String eventPayload,
        String actor,
        Instant occurredAt,
        Instant receivedAt
) {

    public AuditEvent {
        Objects.requireNonNull(auditId, "auditId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(eventPayload, "eventPayload must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static AuditEvent create(
            UUID correlationId,
            UUID paymentId,
            String serviceName,
            String eventType,
            String eventPayload,
            String actor,
            Instant occurredAt
    ) {
        return new AuditEvent(
                UUID.randomUUID(),
                correlationId,
                paymentId,
                serviceName,
                eventType,
                eventPayload,
                actor,
                occurredAt,
                Instant.now()
        );
    }
}
