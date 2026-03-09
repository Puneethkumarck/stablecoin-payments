package com.stablecoin.payments.offramp.domain.port;

import com.stablecoin.payments.offramp.domain.model.OffRampTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OffRampTransactionRepository {

    OffRampTransaction save(OffRampTransaction transaction);

    Optional<OffRampTransaction> findById(UUID offRampTxnId);

    List<OffRampTransaction> findByPayoutId(UUID payoutId);
}
