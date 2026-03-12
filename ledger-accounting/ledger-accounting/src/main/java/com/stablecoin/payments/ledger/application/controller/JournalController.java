package com.stablecoin.payments.ledger.application.controller;

import com.stablecoin.payments.ledger.api.AccountHistoryResponse;
import com.stablecoin.payments.ledger.api.AccountHistoryResponse.HistoryEntry;
import com.stablecoin.payments.ledger.api.PaymentJournalResponse;
import com.stablecoin.payments.ledger.api.PaymentJournalResponse.EntryResponse;
import com.stablecoin.payments.ledger.api.PaymentJournalResponse.LegSummary;
import com.stablecoin.payments.ledger.api.PaymentJournalResponse.ReconciliationSummary;
import com.stablecoin.payments.ledger.api.PaymentJournalResponse.TransactionResponse;
import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import com.stablecoin.payments.ledger.domain.service.LedgerQueryHandler;
import com.stablecoin.payments.ledger.domain.service.LedgerQueryHandler.AccountHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/ledger")
@RequiredArgsConstructor
public class JournalController {

    private final LedgerQueryHandler queryHandler;
    private final ReconciliationRepository reconciliationRepository;

    @GetMapping("/payments/{paymentId}/journal")
    public PaymentJournalResponse getPaymentJournal(@PathVariable UUID paymentId) {
        log.info("GET /v1/ledger/payments/{}/journal", paymentId);
        var transactions = queryHandler.getPaymentJournal(paymentId);
        var reconciliation = reconciliationRepository.findByPaymentId(paymentId)
                .map(this::toReconciliationSummary)
                .orElse(null);
        return toPaymentJournalResponse(paymentId, transactions, reconciliation);
    }

    @GetMapping("/accounts/{accountCode}/history")
    public AccountHistoryResponse getAccountHistory(
            @PathVariable String accountCode,
            @RequestParam String currency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /v1/ledger/accounts/{}/history currency={} page={} size={}",
                accountCode, currency, page, size);
        var history = queryHandler.getAccountHistory(accountCode, currency, page, size);
        return toAccountHistoryResponse(history);
    }

    private PaymentJournalResponse toPaymentJournalResponse(
            UUID paymentId,
            List<LedgerTransaction> transactions,
            ReconciliationSummary reconciliation) {
        var status = reconciliation != null ? reconciliation.status() : "IN_PROGRESS";
        var transactionResponses = transactions.stream()
                .map(this::toTransactionResponse)
                .toList();
        return new PaymentJournalResponse(paymentId, status, transactionResponses, reconciliation);
    }

    private TransactionResponse toTransactionResponse(LedgerTransaction tx) {
        var entries = tx.entries().stream()
                .map(this::toEntryResponse)
                .toList();
        return new TransactionResponse(
                tx.transactionId(),
                tx.sourceEvent(),
                tx.description(),
                tx.createdAt(),
                entries
        );
    }

    private EntryResponse toEntryResponse(JournalEntry entry) {
        return new EntryResponse(
                entry.entryId(),
                entry.sequenceNo(),
                entry.entryType().name(),
                entry.accountCode(),
                entry.amount(),
                entry.currency(),
                entry.balanceAfter()
        );
    }

    private ReconciliationSummary toReconciliationSummary(ReconciliationRecord record) {
        var legs = record.legs().stream()
                .map(leg -> new LegSummary(
                        leg.legType().name(),
                        leg.amount(),
                        leg.currency()))
                .toList();
        return new ReconciliationSummary(record.status().name(), legs, null);
    }

    private AccountHistoryResponse toAccountHistoryResponse(AccountHistory history) {
        var entries = history.entries().stream()
                .map(this::toHistoryEntry)
                .toList();
        return new AccountHistoryResponse(
                history.accountCode(),
                history.currency(),
                entries,
                history.page(),
                history.size(),
                history.totalElements()
        );
    }

    private HistoryEntry toHistoryEntry(JournalEntry entry) {
        return new HistoryEntry(
                entry.entryId(),
                entry.sequenceNo(),
                entry.entryType().name(),
                entry.amount(),
                entry.balanceAfter(),
                entry.paymentId(),
                entry.sourceEvent(),
                entry.createdAt()
        );
    }
}
