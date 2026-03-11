package com.stablecoin.payments.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReconciliationLeg(
        UUID legId,
        UUID recId,
        ReconciliationLegType legType,
        BigDecimal amount,
        String currency,
        UUID sourceEventId,
        Instant receivedAt
) {

    public ReconciliationLeg {
        Objects.requireNonNull(legId, "legId must not be null");
        Objects.requireNonNull(recId, "recId must not be null");
        Objects.requireNonNull(legType, "legType must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }
}
