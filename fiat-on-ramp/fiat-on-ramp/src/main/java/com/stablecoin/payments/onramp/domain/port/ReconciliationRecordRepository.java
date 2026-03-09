package com.stablecoin.payments.onramp.domain.port;

import com.stablecoin.payments.onramp.domain.model.ReconciliationRecord;

import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRecordRepository {

    ReconciliationRecord save(ReconciliationRecord record);

    Optional<ReconciliationRecord> findByCollectionId(UUID collectionId);

    boolean existsByCollectionId(UUID collectionId);
}
