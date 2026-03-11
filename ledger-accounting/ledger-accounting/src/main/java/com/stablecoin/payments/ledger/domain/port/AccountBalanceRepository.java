package com.stablecoin.payments.ledger.domain.port;

import com.stablecoin.payments.ledger.domain.model.AccountBalance;

import java.util.List;
import java.util.Optional;

public interface AccountBalanceRepository {

    AccountBalance save(AccountBalance balance);

    Optional<AccountBalance> findByAccountCodeAndCurrency(String accountCode, String currency);

    /**
     * Finds the balance with a pessimistic lock (FOR UPDATE) for safe concurrent updates.
     */
    Optional<AccountBalance> findForUpdate(String accountCode, String currency);

    List<AccountBalance> findAll();

    List<AccountBalance> findByAccountCode(String accountCode);
}
