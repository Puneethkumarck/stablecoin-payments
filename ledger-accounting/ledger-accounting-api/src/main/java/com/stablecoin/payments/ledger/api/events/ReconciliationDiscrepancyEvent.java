package com.stablecoin.payments.ledger.api.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationDiscrepancyEvent(
        int schemaVersion,
        UUID eventId,
        String eventType,
        UUID recId,
        UUID paymentId,
        BigDecimal discrepancy,
        String currency,
        String detail,
        Instant detectedAt
) {
    public static final String EVENT_TYPE = "reconciliation.discrepancy";
    public static final String TOPIC = "reconciliation.discrepancy";
    public static final int SCHEMA_VERSION = 1;
}
