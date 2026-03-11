package com.stablecoin.payments.ledger.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationResponse(
        UUID recId,
        UUID paymentId,
        String status,
        List<LegResponse> legs,
        BigDecimal appliedFxRate,
        BigDecimal expectedFiatOut,
        BigDecimal discrepancy,
        Instant reconciledAt
) {
    public record LegResponse(
            String legType,
            BigDecimal amount,
            String currency,
            Instant receivedAt
    ) {}
}
