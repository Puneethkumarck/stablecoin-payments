package com.stablecoin.payments.ledger.infrastructure.persistence.adapter;

import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.domain.port.AccountBalanceRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AccountBalanceId;
import com.stablecoin.payments.ledger.infrastructure.persistence.mapper.AccountBalancePersistenceMapper;
import com.stablecoin.payments.ledger.infrastructure.persistence.repository.AccountBalanceJpaRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.updater.AccountBalanceEntityUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountBalancePersistenceAdapter implements AccountBalanceRepository {

    private final AccountBalanceJpaRepository jpa;
    private final AccountBalancePersistenceMapper mapper;
    private final AccountBalanceEntityUpdater updater;

    @Override
    public AccountBalance save(AccountBalance balance) {
        var id = new AccountBalanceId(balance.accountCode(), balance.currency());
        var existing = jpa.findById(id);
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), balance);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(balance)));
    }

    @Override
    public Optional<AccountBalance> findByAccountCodeAndCurrency(String accountCode, String currency) {
        return jpa.findByAccountCodeAndCurrency(accountCode, currency)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<AccountBalance> findForUpdate(String accountCode, String currency) {
        return jpa.findForUpdate(accountCode, currency)
                .map(mapper::toDomain);
    }

    @Override
    public List<AccountBalance> findAll() {
        return jpa.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<AccountBalance> findByAccountCode(String accountCode) {
        return jpa.findByAccountCode(accountCode).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
