package com.stablecoin.payments.ledger.infrastructure.persistence.repository;

import com.stablecoin.payments.ledger.infrastructure.persistence.entity.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerTransactionJpaRepository extends JpaRepository<LedgerTransactionEntity, UUID> {

    List<LedgerTransactionEntity> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    boolean existsBySourceEventId(UUID sourceEventId);
}
