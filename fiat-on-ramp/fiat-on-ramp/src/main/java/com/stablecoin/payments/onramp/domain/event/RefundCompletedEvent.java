package com.stablecoin.payments.onramp.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundCompletedEvent(
        UUID refundId,
        UUID collectionId,
        UUID paymentId,
        BigDecimal refundAmount,
        String currency,
        String pspRefundRef,
        Instant completedAt
) {

    public static final String TOPIC = "fiat.refund.completed";
}
