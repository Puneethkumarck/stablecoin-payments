package com.stablecoin.payments.ledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_legs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationLegEntity {

    @Id
    @Column(name = "leg_id", updatable = false)
    private UUID legId;

    @Column(name = "rec_id", nullable = false, updatable = false)
    private UUID recId;

    @Column(name = "leg_type", length = 30, nullable = false, updatable = false)
    private String legType;

    @Column(name = "amount", precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
}
