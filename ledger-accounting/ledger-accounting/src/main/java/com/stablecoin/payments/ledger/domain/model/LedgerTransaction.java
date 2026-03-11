package com.stablecoin.payments.ledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for the three-object model.
 * Groups balanced journal entry pairs — entries only created through transactions.
 * Immutable once created.
 */
public record LedgerTransaction(
        UUID transactionId,
        UUID paymentId,
        UUID correlationId,
        String sourceEvent,
        UUID sourceEventId,
        String description,
        List<JournalEntry> entries,
        Instant createdAt
) {

    public LedgerTransaction {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(sourceEvent, "sourceEvent must not be null");
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(entries, "entries must not be null");
        entries = List.copyOf(entries);
        if (entries.size() < 2) {
            throw new IllegalArgumentException("A transaction must have at least 2 entries (balanced pair)");
        }
        validateBalance(entries);
    }

    private static void validateBalance(List<JournalEntry> entries) {
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        for (JournalEntry entry : entries) {
            if (entry.entryType() == EntryType.DEBIT) {
                totalDebits = totalDebits.add(entry.amount());
            } else {
                totalCredits = totalCredits.add(entry.amount());
            }
        }
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalArgumentException(
                    "Transaction is not balanced: debits=" + totalDebits + " credits=" + totalCredits
            );
        }
    }

    public BigDecimal totalDebits() {
        return entries.stream()
                .filter(e -> e.entryType() == EntryType.DEBIT)
                .map(JournalEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCredits() {
        return entries.stream()
                .filter(e -> e.entryType() == EntryType.CREDIT)
                .map(JournalEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
