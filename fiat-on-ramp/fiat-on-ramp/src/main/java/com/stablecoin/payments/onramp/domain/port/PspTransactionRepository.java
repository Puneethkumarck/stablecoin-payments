package com.stablecoin.payments.onramp.domain.port;

import com.stablecoin.payments.onramp.domain.model.PspTransaction;

import java.util.List;
import java.util.UUID;

public interface PspTransactionRepository {

    PspTransaction save(PspTransaction transaction);

    List<PspTransaction> findByCollectionId(UUID collectionId);
}
