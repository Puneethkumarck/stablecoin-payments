package com.stablecoin.payments.compliance.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ComplianceCheckFailed(
        UUID checkId,
        UUID paymentId,
        UUID correlationId,
        String reason,
        String errorCode,
        Instant failedAt
) {
    public static final String TOPIC = "compliance.result";
}
