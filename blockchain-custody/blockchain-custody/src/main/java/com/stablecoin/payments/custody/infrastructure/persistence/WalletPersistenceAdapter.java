package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.WalletJpaRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.mapper.WalletEntityUpdater;
import com.stablecoin.payments.custody.infrastructure.persistence.mapper.WalletPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class WalletPersistenceAdapter implements WalletRepository {

    private final WalletJpaRepository jpa;
    private final WalletPersistenceMapper mapper;
    private final WalletEntityUpdater updater;

    @Override
    public Wallet save(Wallet wallet) {
        var existing = jpa.findById(wallet.walletId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), wallet);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(wallet)));
    }

    @Override
    public Optional<Wallet> findById(UUID walletId) {
        return jpa.findById(walletId).map(mapper::toDomain);
    }

    @Override
    public List<Wallet> findByChainIdAndPurpose(ChainId chainId, WalletPurpose purpose) {
        return jpa.findByChainIdAndPurposeAndActiveTrue(chainId.value(), purpose).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Wallet> findByAddress(String address) {
        return jpa.findByAddress(address).map(mapper::toDomain);
    }
}
