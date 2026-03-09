package com.stablecoin.payments.onramp.infrastructure.persistence.mapper;

import com.stablecoin.payments.onramp.domain.model.ReconciliationRecord;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.ReconciliationRecordEntity;
import org.mapstruct.Mapper;

@Mapper
public interface ReconciliationRecordPersistenceMapper {

    default ReconciliationRecordEntity toEntity(ReconciliationRecord record) {
        if (record == null) {
            return null;
        }
        return ReconciliationRecordEntity.builder()
                .reconciliationId(record.reconciliationId())
                .collectionId(record.collectionId())
                .psp(record.psp())
                .pspReference(record.pspReference())
                .expectedAmount(record.expectedAmount())
                .actualAmount(record.actualAmount())
                .currency(record.currency())
                .status(record.status())
                .discrepancyType(record.discrepancyType())
                .discrepancyAmount(record.discrepancyAmount())
                .reconciledAt(record.reconciledAt())
                .createdAt(record.createdAt())
                .build();
    }

    default ReconciliationRecord toDomain(ReconciliationRecordEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReconciliationRecord(
                entity.getReconciliationId(),
                entity.getCollectionId(),
                entity.getPsp(),
                entity.getPspReference(),
                entity.getExpectedAmount(),
                entity.getActualAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getDiscrepancyType(),
                entity.getDiscrepancyAmount(),
                entity.getReconciledAt(),
                entity.getCreatedAt()
        );
    }
}
