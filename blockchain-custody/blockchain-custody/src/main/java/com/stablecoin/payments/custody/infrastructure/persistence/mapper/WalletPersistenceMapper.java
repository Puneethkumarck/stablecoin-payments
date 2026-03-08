package com.stablecoin.payments.custody.infrastructure.persistence.mapper;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.WalletEntity;
import org.mapstruct.Mapper;

@Mapper
public interface WalletPersistenceMapper {

    default WalletEntity toEntity(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        return WalletEntity.builder()
                .walletId(wallet.walletId())
                .chainId(wallet.chainId() != null ? wallet.chainId().value() : null)
                .address(wallet.address())
                .addressChecksum(wallet.addressChecksum())
                .tier(wallet.tier())
                .purpose(wallet.purpose())
                .custodian(wallet.custodian())
                .vaultAccountId(wallet.vaultAccountId())
                .stablecoin(wallet.stablecoin() != null ? wallet.stablecoin().ticker() : null)
                .active(wallet.active())
                .createdAt(wallet.createdAt())
                .deactivatedAt(wallet.deactivatedAt())
                .build();
    }

    default Wallet toDomain(WalletEntity entity) {
        if (entity == null) {
            return null;
        }

        ChainId chainId = null;
        if (entity.getChainId() != null) {
            chainId = new ChainId(entity.getChainId());
        }

        StablecoinTicker stablecoin = null;
        if (entity.getStablecoin() != null) {
            stablecoin = StablecoinTicker.of(entity.getStablecoin());
        }

        return new Wallet(
                entity.getWalletId(),
                chainId,
                entity.getAddress(),
                entity.getAddressChecksum(),
                entity.getTier(),
                entity.getPurpose(),
                entity.getCustodian(),
                entity.getVaultAccountId(),
                stablecoin,
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getDeactivatedAt()
        );
    }
}
