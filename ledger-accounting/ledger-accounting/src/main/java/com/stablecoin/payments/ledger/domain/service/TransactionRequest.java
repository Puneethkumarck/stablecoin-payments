package com.stablecoin.payments.ledger.domain.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command object to create a balanced ledger transaction with journal entries.
 */
public record TransactionRequest(
        UUID paymentId,
        UUID correlationId,
        String sourceEvent,
        UUID sourceEventId,
        String description,
        List<JournalEntryRequest> entries
) {

    public TransactionRequest {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(sourceEvent, "sourceEvent must not be null");
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(entries, "entries must not be null");
        entries = List.copyOf(entries);
        if (entries.size() < 2) {
            throw new IllegalArgumentException("At least 2 entries required for a balanced transaction");
        }
    }
}
