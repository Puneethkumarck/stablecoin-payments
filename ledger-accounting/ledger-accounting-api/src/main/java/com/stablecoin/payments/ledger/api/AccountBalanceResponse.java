package com.stablecoin.payments.ledger.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountBalanceResponse(
        String accountCode,
        String accountName,
        String accountType,
        List<CurrencyBalance> balances,
        Instant asOf
) {
    public record CurrencyBalance(
            String currency,
            BigDecimal balance,
            long version
    ) {}
}
