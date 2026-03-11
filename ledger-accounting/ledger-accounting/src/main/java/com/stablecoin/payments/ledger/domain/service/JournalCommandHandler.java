package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.exception.DuplicateTransactionException;
import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.domain.model.AuditEvent;
import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;
import com.stablecoin.payments.ledger.domain.port.AccountBalanceRepository;
import com.stablecoin.payments.ledger.domain.port.AuditEventRepository;
import com.stablecoin.payments.ledger.domain.port.JournalEntryRepository;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Domain command handler that orchestrates posting balanced ledger transactions.
 * Wires AccountingRules → BalanceCalculator → persistence for the full posting flow.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Idempotent event processing via source_event_id uniqueness</li>
 *   <li>Sequence numbering across a payment's entries</li>
 *   <li>Balance computation and AccountBalance persistence</li>
 *   <li>Audit trail creation</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class JournalCommandHandler {

    private static final String SERVICE_NAME = "ledger-accounting";
    private static final String JOURNAL_POSTED_EVENT = "journal.posted";
    private static final String SYSTEM_ACTOR = "system";

    private final LedgerTransactionRepository transactionRepository;
    private final JournalEntryRepository entryRepository;
    private final AccountBalanceRepository balanceRepository;
    private final AuditEventRepository auditEventRepository;
    private final BalanceCalculator balanceCalculator;
    private final Clock clock;

    /**
     * Posts a balanced ledger transaction from a {@link TransactionRequest}.
     * Idempotent: if the source_event_id was already processed, returns the existing transaction.
     *
     * @param request the transaction request (from AccountingRules mapping)
     * @return the posted LedgerTransaction
     */
    public LedgerTransaction postTransaction(TransactionRequest request) {
        if (transactionRepository.existsBySourceEventId(request.sourceEventId())) {
            return findExistingTransaction(request);
        }

        Instant now = clock.instant();
        UUID transactionId = UUID.randomUUID();
        int baseSequence = entryRepository.countByPaymentId(request.paymentId());

        Map<String, BalanceUpdate> balanceUpdates = balanceCalculator.computeBalances(request.entries());

        List<JournalEntry> entries = buildEntries(request, transactionId, baseSequence, balanceUpdates, now);

        LedgerTransaction transaction = new LedgerTransaction(
                transactionId, request.paymentId(), request.correlationId(),
                request.sourceEvent(), request.sourceEventId(), request.description(),
                entries, now
        );

        LedgerTransaction saved = transactionRepository.save(transaction);

        persistBalanceUpdates(entries, balanceUpdates, now);

        saveAuditEvent(request, transactionId, entries.size(), now);

        return saved;
    }

    private LedgerTransaction findExistingTransaction(TransactionRequest request) {
        return transactionRepository.findByPaymentId(request.paymentId()).stream()
                .filter(t -> t.sourceEventId().equals(request.sourceEventId()))
                .findFirst()
                .orElseThrow(() -> new DuplicateTransactionException(request.sourceEventId()));
    }

    private List<JournalEntry> buildEntries(
            TransactionRequest request,
            UUID transactionId,
            int baseSequence,
            Map<String, BalanceUpdate> balanceUpdates,
            Instant now
    ) {
        List<JournalEntry> entries = new ArrayList<>();
        for (int i = 0; i < request.entries().size(); i++) {
            JournalEntryRequest req = request.entries().get(i);
            String key = BalanceCalculator.balanceKey(req.accountCode(), req.currency());
            BalanceUpdate update = balanceUpdates.get(key);

            entries.add(new JournalEntry(
                    UUID.randomUUID(),
                    transactionId,
                    request.paymentId(),
                    request.correlationId(),
                    baseSequence + i + 1,
                    req.entryType(),
                    req.accountCode(),
                    req.amount(),
                    req.currency(),
                    update.balanceAfter(),
                    update.accountVersion(),
                    request.sourceEvent(),
                    request.sourceEventId(),
                    now
            ));
        }
        return entries;
    }

    private void persistBalanceUpdates(
            List<JournalEntry> entries,
            Map<String, BalanceUpdate> updates,
            Instant now
    ) {
        Set<String> persisted = new HashSet<>();
        for (JournalEntry entry : entries) {
            String key = BalanceCalculator.balanceKey(entry.accountCode(), entry.currency());
            if (persisted.add(key)) {
                BalanceUpdate update = updates.get(key);
                balanceRepository.save(new AccountBalance(
                        entry.accountCode(),
                        entry.currency(),
                        update.balanceAfter(),
                        update.accountVersion(),
                        entry.entryId(),
                        now
                ));
            }
        }
    }

    private void saveAuditEvent(
            TransactionRequest request,
            UUID transactionId,
            int entryCount,
            Instant now
    ) {
        String payload = "{\"transactionId\":\"" + transactionId
                + "\",\"sourceEvent\":\"" + request.sourceEvent()
                + "\",\"entryCount\":" + entryCount + "}";

        auditEventRepository.save(new AuditEvent(
                UUID.randomUUID(),
                request.correlationId(),
                request.paymentId(),
                SERVICE_NAME,
                JOURNAL_POSTED_EVENT,
                payload,
                SYSTEM_ACTOR,
                now,
                now
        ));
    }
}
