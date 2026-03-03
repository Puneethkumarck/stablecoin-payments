package com.stablecoin.payments.gateway.iam.infrastructure.persistence.adapter;

import com.stablecoin.payments.gateway.iam.domain.model.AuditLogEntry;
import com.stablecoin.payments.gateway.iam.domain.port.AuditLogRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.AuditLogEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.AuditLogJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final AuditLogJpaRepository jpa;

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        var logId = entry.getLogId() != null ? entry.getLogId() : UUID.randomUUID();
        var occurredAt = entry.getOccurredAt() != null ? entry.getOccurredAt() : Instant.now();
        var entity = AuditLogEntity.builder()
                .logId(logId)
                .merchantId(entry.getMerchantId())
                .action(entry.getAction())
                .resource(entry.getResource())
                .sourceIp(entry.getSourceIp())
                .detail(entry.getDetail())
                .occurredAt(occurredAt)
                .build();
        jpa.save(entity);
        return entry.toBuilder()
                .logId(logId)
                .occurredAt(occurredAt)
                .build();
    }
}
