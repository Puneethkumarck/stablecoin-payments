package com.stablecoin.payments.ledger.api.events;

import java.time.Instant;
import java.util.UUID;

public record ReconciliationCompletedEvent(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID recId,
        UUID paymentId,
        String status,
        Instant completedAt
) {
    public static final String EVENT_TYPE = "reconciliation.completed";
    public static final String TOPIC = "reconciliation.completed";
    public static final int SCHEMA_VERSION = 1;
}
