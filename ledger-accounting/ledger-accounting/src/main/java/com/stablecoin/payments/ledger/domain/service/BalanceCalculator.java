package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.exception.AccountNotFoundException;
import com.stablecoin.payments.ledger.domain.model.Account;
import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.domain.model.EntryType;
import com.stablecoin.payments.ledger.domain.port.AccountBalanceRepository;
import com.stablecoin.payments.ledger.domain.port.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain service that computes balance updates for journal entries.
 * Acquires pessimistic locks on AccountBalance rows in ascending account_code order
 * to prevent deadlocks during concurrent transaction posting.
 *
 * <p>Balance rules:
 * <ul>
 *   <li>ASSET/CLEARING/EXPENSE: DEBIT increases, CREDIT decreases</li>
 *   <li>LIABILITY/REVENUE: CREDIT increases, DEBIT decreases</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BalanceCalculator {

    private final AccountRepository accountRepository;
    private final AccountBalanceRepository balanceRepository;

    /**
     * Computes balance updates for all entries in a transaction.
     * Locks AccountBalance rows in ascending account_code + currency order to prevent deadlocks.
     *
     * @param entries the entry requests to compute balances for
     * @return map of "accountCode:currency" → BalanceUpdate
     */
    public Map<String, BalanceUpdate> computeBalances(List<JournalEntryRequest> entries) {
        List<JournalEntryRequest> sorted = entries.stream()
                .sorted(Comparator.comparing(JournalEntryRequest::accountCode)
                        .thenComparing(JournalEntryRequest::currency))
                .toList();

        Map<String, BalanceUpdate> result = new LinkedHashMap<>();
        for (JournalEntryRequest req : sorted) {
            String key = balanceKey(req.accountCode(), req.currency());
            BalanceUpdate previous = result.get(key);
            result.put(key, computeSingleBalance(req, previous));
        }
        return result;
    }

    private BalanceUpdate computeSingleBalance(JournalEntryRequest entryRequest, BalanceUpdate previous) {
        Account account = accountRepository.findByAccountCode(entryRequest.accountCode())
                .orElseThrow(() -> new AccountNotFoundException(entryRequest.accountCode()));

        BigDecimal currentBalance;
        long newVersion;
        if (previous != null) {
            currentBalance = previous.balanceAfter();
            newVersion = previous.accountVersion();
        } else {
            AccountBalance current = balanceRepository
                    .findForUpdate(entryRequest.accountCode(), entryRequest.currency())
                    .orElse(AccountBalance.zero(entryRequest.accountCode(), entryRequest.currency()));
            currentBalance = current.balance();
            newVersion = current.version() + 1;
        }

        BigDecimal newBalance = computeNewBalance(
                currentBalance, entryRequest.entryType(), account.normalBalance(), entryRequest.amount());

        return new BalanceUpdate(newBalance, newVersion);
    }

    static BigDecimal computeNewBalance(
            BigDecimal currentBalance,
            EntryType entryType,
            EntryType normalBalance,
            BigDecimal amount
    ) {
        if (entryType == normalBalance) {
            return currentBalance.add(amount);
        }
        return currentBalance.subtract(amount);
    }

    /**
     * Builds the composite key for balance lookup: "accountCode:currency".
     */
    public static String balanceKey(String accountCode, String currency) {
        return accountCode + ":" + currency;
    }
}
