package com.stablecoin.payments.offramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.offramp.domain.model.OffRampTransaction;
import com.stablecoin.payments.offramp.infrastructure.persistence.entity.OffRampTransactionEntity;
import org.mapstruct.Mapper;

@Mapper
public interface OffRampTransactionPersistenceMapper {

    default OffRampTransactionEntity toEntity(OffRampTransaction transaction) {
        if (transaction == null) {
            return null;
        }
        return OffRampTransactionEntity.builder()
                .offRampTxnId(transaction.offRampTxnId())
                .payoutId(transaction.payoutId())
                .partnerName(transaction.partnerName())
                .eventType(transaction.eventType())
                .amount(transaction.amount())
                .currency(transaction.currency())
                .status(transaction.status())
                .rawResponse(transaction.rawResponse())
                .receivedAt(transaction.receivedAt())
                .build();
    }

    default OffRampTransaction toDomain(OffRampTransactionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new OffRampTransaction(
                entity.getOffRampTxnId(),
                entity.getPayoutId(),
                entity.getPartnerName(),
                entity.getEventType(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getRawResponse(),
                entity.getReceivedAt()
        );
    }
}
