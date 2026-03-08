package com.stablecoin.payments.orchestrator.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentCompensationStarted(
        UUID paymentId,
        String compensationReason,
        Instant startedAt
) {
    public static final String TOPIC = "payment.compensation.started";
}
