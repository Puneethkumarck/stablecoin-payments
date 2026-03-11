package com.stablecoin.payments.ledger.infrastructure.persistence.adapter;

import com.stablecoin.payments.ledger.domain.model.Account;
import com.stablecoin.payments.ledger.domain.port.AccountRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.mapper.AccountPersistenceMapper;
import com.stablecoin.payments.ledger.infrastructure.persistence.repository.AccountJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;
    private final AccountPersistenceMapper mapper;

    @Override
    public Optional<Account> findByAccountCode(String accountCode) {
        return jpa.findByAccountCode(accountCode).map(mapper::toDomain);
    }

    @Override
    public List<Account> findAll() {
        return jpa.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Account> findByIsActive(boolean isActive) {
        return jpa.findByIsActive(isActive).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
