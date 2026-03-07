package com.stablecoin.payments.compliance.domain.event;

import java.time.Instant;
import java.util.UUID;

public record SanctionsHitEvent(
        UUID checkId,
        UUID paymentId,
        UUID correlationId,
        String hitParty,
        String listName,
        String hitDetails,
        Instant detectedAt
) {
    public static final String TOPIC = "sanctions.hit";
}
