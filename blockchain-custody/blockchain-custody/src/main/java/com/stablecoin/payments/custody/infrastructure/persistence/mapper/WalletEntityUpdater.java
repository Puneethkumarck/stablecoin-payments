package com.stablecoin.payments.custody.infrastructure.persistence.mapper;

import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.WalletEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface WalletEntityUpdater {

    default void updateEntity(@MappingTarget WalletEntity entity, Wallet wallet) {
        if (wallet == null) {
            return;
        }
        entity.setActive(wallet.active());
        entity.setDeactivatedAt(wallet.deactivatedAt());
    }
}
