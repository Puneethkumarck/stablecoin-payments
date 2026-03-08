package com.stablecoin.payments.orchestrator.domain.event;

import com.stablecoin.payments.orchestrator.domain.model.Corridor;
import com.stablecoin.payments.orchestrator.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

public record PaymentInitiated(
        UUID paymentId,
        String idempotencyKey,
        UUID correlationId,
        UUID senderId,
        UUID recipientId,
        Money sourceAmount,
        String targetCurrency,
        Corridor corridor,
        Instant initiatedAt
) {
    public static final String TOPIC = "payment.initiated";
}
