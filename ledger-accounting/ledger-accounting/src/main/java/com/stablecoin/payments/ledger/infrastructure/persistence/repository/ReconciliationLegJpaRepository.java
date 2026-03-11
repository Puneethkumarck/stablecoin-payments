package com.stablecoin.payments.ledger.infrastructure.persistence.repository;

import com.stablecoin.payments.ledger.infrastructure.persistence.entity.ReconciliationLegEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationLegJpaRepository extends JpaRepository<ReconciliationLegEntity, UUID> {

    List<ReconciliationLegEntity> findByRecId(UUID recId);
}
