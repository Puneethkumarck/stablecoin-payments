package com.stablecoin.payments.custody.api;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        UUID paymentId,
        String status,
        String chainId,
        String stablecoin,
        String amount,
        String fromAddress,
        String toAddress,
        String txHash,
        String blockNumber,
        Integer confirmations,
        String gasUsed,
        String gasPriceGwei,
        Integer estimatedConfirmationS,
        Instant confirmedAt,
        Instant createdAt
) {}
