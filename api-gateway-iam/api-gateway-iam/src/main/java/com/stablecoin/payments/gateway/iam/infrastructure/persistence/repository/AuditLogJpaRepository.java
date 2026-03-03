package com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository;

import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, AuditLogEntity.AuditLogId> {
}
