package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.model.AuditLogEntry;

public interface AuditLogRepository {

    AuditLogEntry save(AuditLogEntry entry);
}
