package com.stablecoin.payments.custody.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ReturnConfirmedEvent(
        UUID transferId,
        UUID parentTransferId,
        UUID paymentId,
        UUID correlationId,
        String chainId,
        String txHash,
        Instant confirmedAt
) {
    public static final String TOPIC = "chain.return.confirmed";
}
