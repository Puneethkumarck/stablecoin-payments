package com.stablecoin.payments.custody.domain.port;

import java.math.BigDecimal;

public record TransactionReceipt(
        String txHash,
        long blockNumber,
        boolean success,
        BigDecimal gasUsed,
        BigDecimal effectiveGasPrice,
        int confirmations
) {}
