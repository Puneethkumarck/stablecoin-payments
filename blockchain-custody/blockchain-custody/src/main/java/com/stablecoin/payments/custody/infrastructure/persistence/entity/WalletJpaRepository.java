package com.stablecoin.payments.custody.infrastructure.persistence.entity;

import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletJpaRepository extends JpaRepository<WalletEntity, UUID> {

    List<WalletEntity> findByChainIdAndPurposeAndActiveTrue(String chainId, WalletPurpose purpose);

    Optional<WalletEntity> findByAddress(String address);
}
