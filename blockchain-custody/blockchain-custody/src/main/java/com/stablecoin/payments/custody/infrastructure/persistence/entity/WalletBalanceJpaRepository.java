package com.stablecoin.payments.custody.infrastructure.persistence.entity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletBalanceJpaRepository extends JpaRepository<WalletBalanceEntity, UUID> {

    Optional<WalletBalanceEntity> findByWalletIdAndStablecoin(UUID walletId, String stablecoin);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wb FROM WalletBalanceEntity wb WHERE wb.walletId = :walletId AND wb.stablecoin = :stablecoin")
    Optional<WalletBalanceEntity> findByWalletIdAndStablecoinForUpdate(
            @Param("walletId") UUID walletId,
            @Param("stablecoin") String stablecoin);

    List<WalletBalanceEntity> findByWalletId(UUID walletId);
}
