package com.stablecoin.payments.ledger.domain.port;

import com.stablecoin.payments.ledger.domain.model.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findByAccountCode(String accountCode);

    List<Account> findAll();

    List<Account> findByIsActive(boolean isActive);
}
