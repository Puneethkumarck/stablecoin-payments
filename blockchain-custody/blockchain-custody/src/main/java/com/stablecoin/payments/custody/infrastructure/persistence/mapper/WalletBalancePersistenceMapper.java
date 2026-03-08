package com.stablecoin.payments.custody.infrastructure.persistence.mapper;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.WalletBalance;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.WalletBalanceEntity;
import org.mapstruct.Mapper;

@Mapper
public interface WalletBalancePersistenceMapper {

    default WalletBalanceEntity toEntity(WalletBalance balance) {
        if (balance == null) {
            return null;
        }
        return WalletBalanceEntity.builder()
                .balanceId(balance.balanceId())
                .walletId(balance.walletId())
                .chainId(balance.chainId() != null ? balance.chainId().value() : null)
                .stablecoin(balance.stablecoin() != null ? balance.stablecoin().ticker() : null)
                .availableBalance(balance.availableBalance())
                .reservedBalance(balance.reservedBalance())
                .blockchainBalance(balance.blockchainBalance())
                .lastIndexedBlock(balance.lastIndexedBlock())
                .updatedAt(balance.updatedAt())
                .build();
    }

    default WalletBalance toDomain(WalletBalanceEntity entity) {
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

        return new WalletBalance(
                entity.getBalanceId(),
                entity.getWalletId(),
                chainId,
                stablecoin,
                entity.getAvailableBalance(),
                entity.getReservedBalance(),
                entity.getBlockchainBalance(),
                entity.getLastIndexedBlock(),
                entity.getVersion() != null ? entity.getVersion() : 0L,
                entity.getUpdatedAt()
        );
    }
}
