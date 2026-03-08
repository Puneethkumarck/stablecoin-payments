package com.stablecoin.payments.orchestrator.infrastructure.persistence.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAuditLogJpaRepository extends JpaRepository<PaymentAuditLogEntity, UUID> {

    List<PaymentAuditLogEntity> findByPaymentIdOrderByOccurredAtDesc(UUID paymentId);
}
