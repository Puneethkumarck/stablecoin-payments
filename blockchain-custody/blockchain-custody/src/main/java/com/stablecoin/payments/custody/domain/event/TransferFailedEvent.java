package com.stablecoin.payments.custody.domain.event;

import java.time.Instant;
import java.util.UUID;

public record TransferFailedEvent(
        UUID transferId,
        UUID paymentId,
        UUID correlationId,
        String chainId,
        String reason,
        String errorCode,
        Instant failedAt
) {
    public static final String TOPIC = "chain.transfer.failed";
}
