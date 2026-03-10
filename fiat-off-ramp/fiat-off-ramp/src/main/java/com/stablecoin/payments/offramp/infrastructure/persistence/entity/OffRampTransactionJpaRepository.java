package com.stablecoin.payments.offramp.infrastructure.persistence.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OffRampTransactionJpaRepository extends JpaRepository<OffRampTransactionEntity, UUID> {

    List<OffRampTransactionEntity> findByPayoutIdOrderByReceivedAtAscOffRampTxnIdAsc(UUID payoutId);
}
