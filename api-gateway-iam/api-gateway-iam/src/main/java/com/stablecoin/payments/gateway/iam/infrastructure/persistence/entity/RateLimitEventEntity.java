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

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rate_limit_events")
@IdClass(RateLimitEventEntity.RateLimitEventId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Id
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "limit_value", nullable = false)
    private int limitValue;

    @Column(name = "breached", nullable = false)
    private boolean breached;

    public record RateLimitEventId(UUID eventId, Instant occurredAt) implements Serializable {}
}
