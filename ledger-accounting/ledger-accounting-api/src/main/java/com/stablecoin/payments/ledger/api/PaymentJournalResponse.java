package com.stablecoin.payments.ledger.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentJournalResponse(
        UUID paymentId,
        String status,
        List<TransactionResponse> transactions,
        ReconciliationSummary reconciliation
) {
    public record TransactionResponse(
            UUID transactionId,
            String sourceEvent,
            String description,
            Instant createdAt,
            List<EntryResponse> entries
    ) {}

    public record EntryResponse(
            UUID entryId,
            int sequenceNo,
            String entryType,
            String accountCode,
            BigDecimal amount,
            String currency,
            BigDecimal balanceAfter
    ) {}

    public record ReconciliationSummary(
            String status,
            List<LegSummary> legs,
            BigDecimal discrepancy
    ) {}

    public record LegSummary(
            String legType,
            BigDecimal amount,
            String currency
    ) {}
}
