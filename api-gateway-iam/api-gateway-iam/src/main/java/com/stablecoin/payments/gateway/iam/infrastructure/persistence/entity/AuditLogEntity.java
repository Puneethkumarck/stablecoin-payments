package com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity;

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

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "gateway_audit_log")
@IdClass(AuditLogEntity.AuditLogId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @Column(name = "log_id", nullable = false, updatable = false)
    private UUID logId;

    @Id
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "resource")
    private String resource;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "detail", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> detail;

    public record AuditLogId(UUID logId, Instant occurredAt) implements Serializable {}
}
