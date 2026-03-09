package com.stablecoin.payments.onramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.PspTransaction;
import com.stablecoin.payments.onramp.domain.model.PspTransactionDirection;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.PspTransactionEntity;
import org.mapstruct.Mapper;

@Mapper
public interface PspTransactionPersistenceMapper {

    default PspTransactionEntity toEntity(PspTransaction txn) {
        if (txn == null) {
            return null;
        }
        return PspTransactionEntity.builder()
                .pspTransactionId(txn.pspTxnId())
                .collectionId(txn.collectionId())
                .psp(txn.pspName())
                .pspReference(txn.pspReference())
                .direction(txn.direction() != null ? txn.direction().name() : null)
                .eventType(txn.eventType())
                .status(txn.status())
                .amount(txn.amount() != null ? txn.amount().amount() : null)
                .currency(txn.amount() != null ? txn.amount().currency() : null)
                .rawResponse(txn.rawResponse() != null ? txn.rawResponse() : "{}")
                .receivedAt(txn.receivedAt())
                .build();
    }

    default PspTransaction toDomain(PspTransactionEntity entity) {
        if (entity == null) {
            return null;
        }

        Money amount = null;
        if (entity.getAmount() != null && entity.getCurrency() != null) {
            amount = new Money(entity.getAmount(), entity.getCurrency());
        }

        PspTransactionDirection direction = null;
        if (entity.getDirection() != null) {
            direction = PspTransactionDirection.valueOf(entity.getDirection());
        }

        return new PspTransaction(
                entity.getPspTransactionId(),
                entity.getCollectionId(),
                entity.getPsp(),
                entity.getPspReference(),
                direction,
                entity.getEventType(),
                amount,
                entity.getStatus(),
                entity.getRawResponse(),
                entity.getReceivedAt()
        );
    }
}
