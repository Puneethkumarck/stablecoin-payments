package com.stablecoin.payments.custody.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferConfirmedEvent(
        UUID transferId,
        UUID paymentId,
        UUID correlationId,
        String chainId,
        String txHash,
        long blockNumber,
        int confirmations,
        BigDecimal gasUsed,
        Instant confirmedAt
) {
    public static final String TOPIC = "chain.transfer.confirmed";
}
