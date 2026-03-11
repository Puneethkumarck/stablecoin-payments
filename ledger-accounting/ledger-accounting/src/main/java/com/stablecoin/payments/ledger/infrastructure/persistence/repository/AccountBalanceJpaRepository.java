package com.stablecoin.payments.ledger.infrastructure.persistence.repository;

import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AccountBalanceEntity;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AccountBalanceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountBalanceJpaRepository extends JpaRepository<AccountBalanceEntity, AccountBalanceId> {

    Optional<AccountBalanceEntity> findByAccountCodeAndCurrency(String accountCode, String currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ab FROM AccountBalanceEntity ab WHERE ab.accountCode = :accountCode AND ab.currency = :currency")
    Optional<AccountBalanceEntity> findForUpdate(
            @Param("accountCode") String accountCode,
            @Param("currency") String currency
    );

    List<AccountBalanceEntity> findByAccountCode(String accountCode);
}
