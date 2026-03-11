package com.stablecoin.payments.ledger.infrastructure.persistence.updater;

import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.ReconciliationRecordEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface ReconciliationRecordEntityUpdater {

    default void updateEntity(@MappingTarget ReconciliationRecordEntity entity, ReconciliationRecord record) {
        if (record == null) return;
        entity.setStatus(record.status().name());
        entity.setTolerance(record.tolerance());
        entity.setReconciledAt(record.reconciledAt());
        entity.setUpdatedAt(record.updatedAt());
        // version is managed by @Version (Hibernate auto-increments)
        // recId, paymentId, createdAt are immutable — not updated
    }
}
