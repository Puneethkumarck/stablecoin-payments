package com.stablecoin.payments.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Real-time running balance per account+currency.
 * Updated on every journal entry with optimistic locking via version.
 */
public record AccountBalance(
        String accountCode,
        String currency,
        BigDecimal balance,
        long version,
        UUID lastEntryId,
        Instant updatedAt
) {

    public AccountBalance {
        Objects.requireNonNull(accountCode, "accountCode must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(balance, "balance must not be null");
    }

    /**
     * Creates an initial zero balance for an account+currency pair.
     */
    public static AccountBalance zero(String accountCode, String currency) {
        return new AccountBalance(
                accountCode,
                currency,
                BigDecimal.ZERO,
                0L,
                null,
                Instant.now()
        );
    }

    /**
     * Applies a journal entry to this balance and returns a new AccountBalance with updated values.
     * DEBIT increases ASSET/EXPENSE/CLEARING, decreases LIABILITY/REVENUE.
     * CREDIT decreases ASSET/EXPENSE/CLEARING, increases LIABILITY/REVENUE.
     * The caller is responsible for determining the sign — this method simply adds or subtracts.
     */
    public AccountBalance applyEntry(JournalEntry entry, EntryType normalBalance) {
        Objects.requireNonNull(entry, "entry must not be null");
        Objects.requireNonNull(normalBalance, "normalBalance must not be null");
        if (!this.accountCode.equals(entry.accountCode())) {
            throw new IllegalArgumentException(
                    "Entry account code " + entry.accountCode() + " does not match balance account code " + this.accountCode);
        }
        if (!this.currency.equals(entry.currency())) {
            throw new IllegalArgumentException(
                    "Entry currency " + entry.currency() + " does not match balance currency " + this.currency);
        }

        BigDecimal newBalance;
        if (entry.entryType() == normalBalance) {
            newBalance = this.balance.add(entry.amount());
        } else {
            newBalance = this.balance.subtract(entry.amount());
        }

        return new AccountBalance(
                this.accountCode,
                this.currency,
                newBalance,
                this.version + 1,
                entry.entryId(),
                Instant.now()
        );
    }
}
