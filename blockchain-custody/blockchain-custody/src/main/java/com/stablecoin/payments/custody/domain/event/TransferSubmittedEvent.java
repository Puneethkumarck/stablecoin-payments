package com.stablecoin.payments.custody.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferSubmittedEvent(
        UUID transferId,
        UUID paymentId,
        UUID correlationId,
        String chainId,
        String stablecoin,
        BigDecimal amount,
        String txHash,
        String fromAddress,
        String toAddress,
        Instant submittedAt
) {
    public static final String TOPIC = "chain.transfer.submitted";
}
