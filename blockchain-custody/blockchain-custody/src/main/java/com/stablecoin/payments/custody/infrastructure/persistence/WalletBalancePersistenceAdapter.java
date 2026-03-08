package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.WalletBalance;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.WalletBalanceJpaRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.mapper.WalletBalanceEntityUpdater;
import com.stablecoin.payments.custody.infrastructure.persistence.mapper.WalletBalancePersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class WalletBalancePersistenceAdapter implements WalletBalanceRepository {

    private final WalletBalanceJpaRepository jpa;
    private final WalletBalancePersistenceMapper mapper;
    private final WalletBalanceEntityUpdater updater;

    @Override
    public WalletBalance save(WalletBalance balance) {
        var existing = jpa.findById(balance.balanceId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), balance);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(balance)));
    }

    @Override
    public Optional<WalletBalance> findByWalletIdAndStablecoin(UUID walletId, StablecoinTicker stablecoin) {
        return jpa.findByWalletIdAndStablecoin(walletId, stablecoin.ticker())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<WalletBalance> findByWalletIdAndStablecoinForUpdate(UUID walletId, StablecoinTicker stablecoin) {
        return jpa.findByWalletIdAndStablecoinForUpdate(walletId, stablecoin.ticker())
                .map(mapper::toDomain);
    }

    @Override
    public List<WalletBalance> findByWalletId(UUID walletId) {
        return jpa.findByWalletId(walletId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
