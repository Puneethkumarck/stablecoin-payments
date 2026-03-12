package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.exception.AccountNotFoundException;
import com.stablecoin.payments.ledger.domain.exception.JournalNotFoundException;
import com.stablecoin.payments.ledger.domain.exception.ReconciliationNotFoundException;
import com.stablecoin.payments.ledger.domain.model.Account;
import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.domain.model.EntryType;
import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.port.AccountBalanceRepository;
import com.stablecoin.payments.ledger.domain.port.AccountRepository;
import com.stablecoin.payments.ledger.domain.port.JournalEntryRepository;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only query handler for ledger data.
 * Not @Transactional — all methods are pure reads with no state changes.
 */
@Service
@RequiredArgsConstructor
public class LedgerQueryHandler {

    private final LedgerTransactionRepository transactionRepository;
    private final JournalEntryRepository entryRepository;
    private final AccountBalanceRepository balanceRepository;
    private final AccountRepository accountRepository;
    private final ReconciliationRepository reconciliationRepository;

    /**
     * Returns all ledger transactions with their entries for a payment.
     * Throws JournalNotFoundException if no transactions exist.
     */
    public List<LedgerTransaction> getPaymentJournal(UUID paymentId) {
        var transactions = transactionRepository.findByPaymentId(paymentId);
        if (transactions.isEmpty()) {
            throw new JournalNotFoundException(paymentId);
        }
        return transactions;
    }

    /**
     * Returns the reconciliation record with all legs for a payment.
     */
    public ReconciliationRecord getReconciliation(UUID paymentId) {
        return reconciliationRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ReconciliationNotFoundException(paymentId));
    }

    /**
     * Returns account info + multi-currency balances for a given account code.
     */
    public AccountWithBalances getAccountBalance(String accountCode) {
        var account = accountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));
        var balances = balanceRepository.findByAccountCode(accountCode);
        return new AccountWithBalances(account, balances);
    }

    /**
     * Returns paginated journal entries for a specific account + currency.
     */
    public AccountHistory getAccountHistory(String accountCode, String currency, int page, int size) {
        accountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));
        int offset = page * size;
        var entries = entryRepository.findByAccountCodeAndCurrency(accountCode, currency, offset, size);
        long totalElements = entryRepository.countByAccountCodeAndCurrency(accountCode, currency);
        return new AccountHistory(accountCode, currency, entries, page, size, totalElements);
    }

    /**
     * Returns trial balance — all active accounts with debit/credit balance split.
     */
    public TrialBalance getTrialBalance() {
        var accounts = accountRepository.findByIsActive(true);
        var allBalances = balanceRepository.findAll();

        var totalDebits = BigDecimal.ZERO;
        var totalCredits = BigDecimal.ZERO;

        var lines = new java.util.ArrayList<TrialBalanceLine>();
        for (var account : accounts) {
            var accountBalances = allBalances.stream()
                    .filter(b -> b.accountCode().equals(account.accountCode()))
                    .toList();

            var netBalance = accountBalances.stream()
                    .map(AccountBalance::balance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal debitBalance;
            BigDecimal creditBalance;
            if (account.normalBalance() == EntryType.DEBIT) {
                debitBalance = netBalance;
                creditBalance = BigDecimal.ZERO;
            } else {
                debitBalance = BigDecimal.ZERO;
                creditBalance = netBalance;
            }

            totalDebits = totalDebits.add(debitBalance);
            totalCredits = totalCredits.add(creditBalance);

            lines.add(new TrialBalanceLine(account, debitBalance, creditBalance));
        }

        boolean balanced = totalDebits.compareTo(totalCredits) == 0;
        return new TrialBalance(lines, totalDebits, totalCredits, balanced);
    }

    public record AccountWithBalances(Account account, List<AccountBalance> balances) {}

    public record AccountHistory(
            String accountCode,
            String currency,
            List<JournalEntry> entries,
            int page,
            int size,
            long totalElements
    ) {}

    public record TrialBalanceLine(Account account, BigDecimal debitBalance, BigDecimal creditBalance) {}

    public record TrialBalance(
            List<TrialBalanceLine> lines,
            BigDecimal totalDebits,
            BigDecimal totalCredits,
            boolean balanced
    ) {}
}
