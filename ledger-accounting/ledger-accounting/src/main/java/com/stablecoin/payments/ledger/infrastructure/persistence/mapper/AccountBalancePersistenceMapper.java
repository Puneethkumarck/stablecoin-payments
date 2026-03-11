package com.stablecoin.payments.ledger.infrastructure.persistence.mapper;

import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AccountBalanceEntity;
import org.mapstruct.Mapper;

@Mapper
public interface AccountBalancePersistenceMapper {

    default AccountBalanceEntity toEntity(AccountBalance balance) {
        if (balance == null) return null;
        return AccountBalanceEntity.builder()
                .accountCode(balance.accountCode())
                .currency(balance.currency())
                .balance(balance.balance())
                // version left null — Hibernate @Version treats null as "new entity" → persist()
                .lastEntryId(balance.lastEntryId())
                .updatedAt(balance.updatedAt())
                .build();
    }

    default AccountBalance toDomain(AccountBalanceEntity entity) {
        if (entity == null) return null;
        return new AccountBalance(
                entity.getAccountCode(),
                entity.getCurrency(),
                entity.getBalance(),
                entity.getVersion(),
                entity.getLastEntryId(),
                entity.getUpdatedAt()
        );
    }
}
