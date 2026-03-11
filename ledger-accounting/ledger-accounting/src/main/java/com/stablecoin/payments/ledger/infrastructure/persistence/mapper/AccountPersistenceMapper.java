package com.stablecoin.payments.ledger.infrastructure.persistence.mapper;

import com.stablecoin.payments.ledger.domain.model.Account;
import com.stablecoin.payments.ledger.domain.model.AccountType;
import com.stablecoin.payments.ledger.domain.model.EntryType;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AccountEntity;
import org.mapstruct.Mapper;

@Mapper
public interface AccountPersistenceMapper {

    default Account toDomain(AccountEntity entity) {
        if (entity == null) return null;
        return new Account(
                entity.getAccountCode(),
                entity.getAccountName(),
                AccountType.valueOf(entity.getAccountType()),
                EntryType.valueOf(entity.getNormalBalance()),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }
}
