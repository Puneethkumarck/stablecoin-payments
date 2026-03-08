package com.stablecoin.payments.orchestrator.domain.event;

import com.stablecoin.payments.orchestrator.domain.model.PaymentState;

import java.time.Instant;
import java.util.UUID;

public record PaymentStateAdvanced(
        UUID paymentId,
        UUID correlationId,
        PaymentState fromState,
        PaymentState toState,
        Instant advancedAt,
        String triggeredBy
) {
    public static final String TOPIC = "payment.state.advanced";
}
