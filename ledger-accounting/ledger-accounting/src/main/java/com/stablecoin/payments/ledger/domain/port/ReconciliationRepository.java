package com.stablecoin.payments.ledger.domain.port;

import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.model.ReconciliationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRepository {

    ReconciliationRecord save(ReconciliationRecord record);

    Optional<ReconciliationRecord> findById(UUID recId);

    Optional<ReconciliationRecord> findByPaymentId(UUID paymentId);

    List<ReconciliationRecord> findByStatus(ReconciliationStatus status);
}
