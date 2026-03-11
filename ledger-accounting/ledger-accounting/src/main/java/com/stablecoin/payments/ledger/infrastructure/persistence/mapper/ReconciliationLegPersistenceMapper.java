package com.stablecoin.payments.ledger.infrastructure.persistence.mapper;

import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.ReconciliationLegEntity;
import org.mapstruct.Mapper;

@Mapper
public interface ReconciliationLegPersistenceMapper {

    default ReconciliationLegEntity toEntity(ReconciliationLeg leg) {
        if (leg == null) return null;
        return ReconciliationLegEntity.builder()
                .legId(leg.legId())
                .recId(leg.recId())
                .legType(leg.legType().name())
                .amount(leg.amount())
                .currency(leg.currency())
                .sourceEventId(leg.sourceEventId())
                .receivedAt(leg.receivedAt())
                .build();
    }

    default ReconciliationLeg toDomain(ReconciliationLegEntity entity) {
        if (entity == null) return null;
        return new ReconciliationLeg(
                entity.getLegId(),
                entity.getRecId(),
                ReconciliationLegType.valueOf(entity.getLegType()),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getSourceEventId(),
                entity.getReceivedAt()
        );
    }
}
