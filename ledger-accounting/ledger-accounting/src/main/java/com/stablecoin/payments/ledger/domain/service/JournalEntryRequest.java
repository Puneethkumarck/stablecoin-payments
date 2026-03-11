package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.model.EntryType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representing a single debit or credit entry to be posted.
 * Used by {@link AccountingRules} to define the journal entry template for each event.
 */
public record JournalEntryRequest(
        EntryType entryType,
        String accountCode,
        BigDecimal amount,
        String currency
) {

    public JournalEntryRequest {
        Objects.requireNonNull(entryType, "entryType must not be null");
        Objects.requireNonNull(accountCode, "accountCode must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
