package com.stablecoin.payments.ledger.infrastructure.persistence.updater;

import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AccountBalanceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface AccountBalanceEntityUpdater {

    default void updateEntity(@MappingTarget AccountBalanceEntity entity, AccountBalance balance) {
        if (balance == null) return;
        entity.setBalance(balance.balance());
        entity.setLastEntryId(balance.lastEntryId());
        entity.setUpdatedAt(balance.updatedAt());
        // version is managed by @Version (Hibernate auto-increments)
    }
}
