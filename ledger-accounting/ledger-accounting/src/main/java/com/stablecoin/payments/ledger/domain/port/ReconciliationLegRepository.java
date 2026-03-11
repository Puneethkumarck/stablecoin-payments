package com.stablecoin.payments.ledger.domain.port;

import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;

import java.util.List;
import java.util.UUID;

public interface ReconciliationLegRepository {

    ReconciliationLeg save(ReconciliationLeg leg);

    List<ReconciliationLeg> findByRecId(UUID recId);
}
