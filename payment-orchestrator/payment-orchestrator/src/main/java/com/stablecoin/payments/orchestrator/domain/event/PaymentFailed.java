package com.stablecoin.payments.orchestrator.domain.event;

import com.stablecoin.payments.orchestrator.domain.model.PaymentState;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(
        UUID paymentId,
        PaymentState failedState,
        String reason,
        String errorCode,
        Instant failedAt
) {
    public static final String TOPIC = "payment.failed";
}
