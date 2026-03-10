package com.stablecoin.payments.offramp.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "off_ramp_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffRampTransactionEntity {

    @Id
    @Column(name = "offramp_txn_id", updatable = false, nullable = false)
    private UUID offRampTxnId;

    @Column(name = "payout_id", nullable = false)
    private UUID payoutId;

    @Column(name = "partner_name", nullable = false, length = 100)
    private String partnerName;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", nullable = false, columnDefinition = "JSONB")
    private String rawResponse = "{}";

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
