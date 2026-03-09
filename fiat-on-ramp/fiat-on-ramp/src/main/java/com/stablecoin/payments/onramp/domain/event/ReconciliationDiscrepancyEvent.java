package com.stablecoin.payments.onramp.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationDiscrepancyEvent(
        UUID reconciliationId,
        UUID collectionId,
        UUID paymentId,
        UUID correlationId,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        BigDecimal discrepancyAmount,
        String currency,
        Instant detectedAt
) {

    public static final String TOPIC = "fiat.reconciliation.discrepancy";
}
