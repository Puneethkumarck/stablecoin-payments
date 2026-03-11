package com.stablecoin.payments.ledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, append-only entity — no @Version, no update methods.
 * Partitioned by occurred_at (composite PK: audit_id + occurred_at).
 */
@Entity
@Table(name = "audit_events")
@IdClass(AuditEventId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventEntity {

    @Id
    @Column(name = "audit_id", updatable = false)
    private UUID auditId;

    @Id
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "service_name", length = 100, nullable = false, updatable = false)
    private String serviceName;

    @Column(name = "event_type", length = 100, nullable = false, updatable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", nullable = false, updatable = false, columnDefinition = "JSONB")
    private String eventPayload;

    @Column(name = "actor", length = 100, updatable = false)
    private String actor;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
}
