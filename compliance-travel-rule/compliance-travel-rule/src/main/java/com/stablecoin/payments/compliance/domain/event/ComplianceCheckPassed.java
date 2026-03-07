package com.stablecoin.payments.compliance.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ComplianceCheckPassed(
        UUID checkId,
        UUID paymentId,
        UUID correlationId,
        int riskScore,
        String riskBand,
        String travelRuleRef,
        Instant passedAt
) {
    public static final String TOPIC = "compliance.result";
}
