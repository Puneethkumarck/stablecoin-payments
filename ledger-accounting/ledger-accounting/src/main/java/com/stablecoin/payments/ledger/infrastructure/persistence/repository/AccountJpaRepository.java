package com.stablecoin.payments.ledger.infrastructure.persistence.repository;

import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, String> {

    Optional<AccountEntity> findByAccountCode(String accountCode);

    List<AccountEntity> findByIsActive(boolean isActive);
}
