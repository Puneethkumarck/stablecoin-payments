package com.stablecoin.payments.compliance.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ComplianceCheckInitiated(
        UUID checkId,
        UUID paymentId,
        UUID correlationId,
        BigDecimal sourceAmount,
        String sourceCurrency,
        String sourceCountry,
        String targetCountry,
        Instant initiatedAt
) {
    public static final String TOPIC = "compliance.initiated";
}
