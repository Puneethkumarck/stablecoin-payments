package com.stablecoin.payments.custody.infrastructure.persistence.mapper;

import com.stablecoin.payments.custody.domain.model.WalletBalance;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.WalletBalanceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface WalletBalanceEntityUpdater {

    default void updateEntity(@MappingTarget WalletBalanceEntity entity, WalletBalance balance) {
        if (balance == null) {
            return;
        }
        entity.setAvailableBalance(balance.availableBalance());
        entity.setReservedBalance(balance.reservedBalance());
        entity.setBlockchainBalance(balance.blockchainBalance());
        entity.setLastIndexedBlock(balance.lastIndexedBlock());
        entity.setUpdatedAt(balance.updatedAt());
    }
}
