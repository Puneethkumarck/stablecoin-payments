package com.stablecoin.payments.onramp.infrastructure.persistence.entity;

import com.stablecoin.payments.onramp.domain.model.RefundStatus;
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
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundEntity {

    @Id
    @Column(name = "refund_id", updatable = false, nullable = false)
    private UUID refundId;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "refund_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal refundAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RefundStatus status;

    @Column(name = "psp_refund_ref", length = 200)
    private String pspRefundRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
