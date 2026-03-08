package com.stablecoin.payments.onramp.infrastructure.persistence.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PspTransactionJpaRepository extends JpaRepository<PspTransactionEntity, UUID> {

    List<PspTransactionEntity> findByCollectionId(UUID collectionId);
}
