package com.stablecoin.payments.custody.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WalletBalanceResponse(
        UUID walletId,
        String address,
        String chainId,
        List<BalanceEntry> balances,
        Instant updatedAt
) {
    public record BalanceEntry(
            String stablecoin,
            String availableBalance,
            String reservedBalance,
            String blockchainBalance,
            String lastIndexedBlock
    ) {}
}
