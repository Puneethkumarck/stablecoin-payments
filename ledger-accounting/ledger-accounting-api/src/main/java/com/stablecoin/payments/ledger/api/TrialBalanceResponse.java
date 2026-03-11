package com.stablecoin.payments.ledger.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TrialBalanceResponse(
        Instant asOf,
        List<AccountLine> accounts,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        boolean balanced
) {
    public record AccountLine(
            String accountCode,
            String accountName,
            String type,
            BigDecimal debitBalance,
            BigDecimal creditBalance
    ) {}
}
