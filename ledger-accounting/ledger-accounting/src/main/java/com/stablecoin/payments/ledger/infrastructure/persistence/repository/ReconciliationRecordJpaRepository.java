package com.stablecoin.payments.ledger.infrastructure.persistence.repository;

import com.stablecoin.payments.ledger.infrastructure.persistence.entity.ReconciliationRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRecordJpaRepository extends JpaRepository<ReconciliationRecordEntity, UUID> {

    Optional<ReconciliationRecordEntity> findByPaymentId(UUID paymentId);

    List<ReconciliationRecordEntity> findByStatus(String status);
}
