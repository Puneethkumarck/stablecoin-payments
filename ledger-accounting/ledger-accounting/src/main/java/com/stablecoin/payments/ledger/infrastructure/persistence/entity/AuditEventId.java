package com.stablecoin.payments.ledger.infrastructure.persistence.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class AuditEventId implements Serializable {

    private UUID auditId;
    private Instant occurredAt;

    public AuditEventId() {
    }

    public AuditEventId(UUID auditId, Instant occurredAt) {
        this.auditId = auditId;
        this.occurredAt = occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEventId that = (AuditEventId) o;
        return Objects.equals(auditId, that.auditId) && Objects.equals(occurredAt, that.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(auditId, occurredAt);
    }
}
