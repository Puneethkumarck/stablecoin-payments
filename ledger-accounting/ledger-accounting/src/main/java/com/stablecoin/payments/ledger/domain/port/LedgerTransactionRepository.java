package com.stablecoin.payments.ledger.domain.port;

import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository {

    LedgerTransaction save(LedgerTransaction transaction);

    Optional<LedgerTransaction> findById(UUID transactionId);

    List<LedgerTransaction> findByPaymentId(UUID paymentId);

    boolean existsBySourceEventId(UUID sourceEventId);
}
