package com.stablecoin.payments.onramp.infrastructure.persistence.entity;

import com.stablecoin.payments.onramp.domain.model.ReconciliationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "reconciliation_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRecordEntity {

    @Id
    @Column(name = "reconciliation_id", updatable = false, nullable = false)
    private UUID reconciliationId;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(name = "psp", nullable = false, length = 50)
    private String psp;

    @Column(name = "psp_reference", nullable = false, length = 200)
    private String pspReference;

    @Column(name = "expected_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", precision = 20, scale = 8)
    private BigDecimal actualAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReconciliationStatus status;

    @Column(name = "discrepancy_type", length = 50)
    private String discrepancyType;

    @Column(name = "discrepancy_amount", precision = 20, scale = 8)
    private BigDecimal discrepancyAmount;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
