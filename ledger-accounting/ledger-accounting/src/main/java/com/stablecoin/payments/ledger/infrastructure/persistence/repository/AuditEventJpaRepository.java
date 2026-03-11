package com.stablecoin.payments.ledger.infrastructure.persistence.repository;

import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AuditEventEntity;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AuditEventId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, AuditEventId> {

    List<AuditEventEntity> findByPaymentIdOrderByOccurredAtDesc(UUID paymentId);

    List<AuditEventEntity> findByCorrelationId(UUID correlationId);
}
