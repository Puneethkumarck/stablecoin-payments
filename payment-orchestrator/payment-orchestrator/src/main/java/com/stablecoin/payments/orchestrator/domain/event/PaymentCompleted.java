package com.stablecoin.payments.orchestrator.domain.event;

import com.stablecoin.payments.orchestrator.domain.model.ChainId;
import com.stablecoin.payments.orchestrator.domain.model.FxRate;
import com.stablecoin.payments.orchestrator.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

public record PaymentCompleted(
        UUID paymentId,
        UUID correlationId,
        Money sourceAmount,
        Money targetAmount,
        FxRate fxRate,
        ChainId chainId,
        String txHash,
        Instant completedAt
) {
    public static final String TOPIC = "payment.completed";
}
