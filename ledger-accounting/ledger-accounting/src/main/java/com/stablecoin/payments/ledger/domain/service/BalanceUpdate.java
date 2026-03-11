package com.stablecoin.payments.ledger.domain.service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representing the result of a balance computation.
 * Contains the new balance_after and account_version for a journal entry.
 */
public record BalanceUpdate(
        BigDecimal balanceAfter,
        long accountVersion
) {

    public BalanceUpdate {
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        if (accountVersion < 1) {
            throw new IllegalArgumentException("accountVersion must be at least 1");
        }
    }
}
