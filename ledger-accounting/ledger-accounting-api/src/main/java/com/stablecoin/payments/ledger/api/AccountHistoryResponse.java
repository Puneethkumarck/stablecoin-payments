package com.stablecoin.payments.ledger.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountHistoryResponse(
        String accountCode,
        String currency,
        List<HistoryEntry> entries,
        int page,
        int size,
        long totalElements
) {
    public record HistoryEntry(
            UUID entryId,
            int sequenceNo,
            String entryType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            UUID paymentId,
            String sourceEvent,
            Instant createdAt
    ) {}
}
