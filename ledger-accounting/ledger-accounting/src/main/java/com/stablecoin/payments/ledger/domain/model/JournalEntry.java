package com.stablecoin.payments.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record JournalEntry(
        UUID entryId,
        UUID transactionId,
        UUID paymentId,
        UUID correlationId,
        int sequenceNo,
        EntryType entryType,
        String accountCode,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        long accountVersion,
        String sourceEvent,
        UUID sourceEventId,
        Instant createdAt
) {

    public JournalEntry {
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(entryType, "entryType must not be null");
        Objects.requireNonNull(accountCode, "accountCode must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        Objects.requireNonNull(sourceEvent, "sourceEvent must not be null");
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
