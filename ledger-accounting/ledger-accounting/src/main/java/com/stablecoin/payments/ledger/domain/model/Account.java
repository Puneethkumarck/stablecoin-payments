package com.stablecoin.payments.ledger.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Account(
        String accountCode,
        String accountName,
        AccountType accountType,
        EntryType normalBalance,
        boolean isActive,
        Instant createdAt
) {

    public Account {
        Objects.requireNonNull(accountCode, "accountCode must not be null");
        Objects.requireNonNull(accountName, "accountName must not be null");
        Objects.requireNonNull(accountType, "accountType must not be null");
        Objects.requireNonNull(normalBalance, "normalBalance must not be null");
    }
}
