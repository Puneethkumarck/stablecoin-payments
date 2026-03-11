package com.stablecoin.payments.ledger.infrastructure.persistence.mapper;

import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.model.ReconciliationStatus;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.ReconciliationRecordEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface ReconciliationRecordPersistenceMapper {

    default ReconciliationRecordEntity toEntity(ReconciliationRecord record) {
        if (record == null) return null;
        return ReconciliationRecordEntity.builder()
                .recId(record.recId())
                .paymentId(record.paymentId())
                .status(record.status().name())
                .tolerance(record.tolerance())
                .reconciledAt(record.reconciledAt())
                .createdAt(record.createdAt())
                .updatedAt(record.updatedAt())
                // version left null — Hibernate @Version treats null as "new entity" → persist()
                .build();
    }

    default ReconciliationRecord toDomain(ReconciliationRecordEntity entity, List<ReconciliationLeg> legs) {
        if (entity == null) return null;
        return new ReconciliationRecord(
                entity.getRecId(),
                entity.getPaymentId(),
                ReconciliationStatus.valueOf(entity.getStatus()),
                entity.getTolerance(),
                entity.getReconciledAt(),
                legs,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
