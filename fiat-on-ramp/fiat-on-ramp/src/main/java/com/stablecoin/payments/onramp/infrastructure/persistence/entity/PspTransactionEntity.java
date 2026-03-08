package com.stablecoin.payments.onramp.infrastructure.persistence.entity;

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
@Table(name = "psp_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PspTransactionEntity {

    @Id
    @Column(name = "psp_transaction_id", updatable = false, nullable = false)
    private UUID pspTransactionId;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(name = "psp", nullable = false, length = 50)
    private String psp;

    @Column(name = "psp_reference", nullable = false, length = 200)
    private String pspReference;

    @Column(name = "direction", length = 20)
    private String direction;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "amount", precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String rawResponse = "{}";

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
