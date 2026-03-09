package com.stablecoin.payments.onramp.infrastructure.persistence.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRecordJpaRepository extends JpaRepository<ReconciliationRecordEntity, UUID> {

    Optional<ReconciliationRecordEntity> findByCollectionId(UUID collectionId);

    boolean existsByCollectionId(UUID collectionId);
}
