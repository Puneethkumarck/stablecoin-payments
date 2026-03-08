package com.stablecoin.payments.onramp.infrastructure.persistence.entity;

import com.stablecoin.payments.onramp.domain.model.CollectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collection_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionOrderEntity {

    @Id
    @Column(name = "collection_id", updatable = false, nullable = false)
    private UUID collectionId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Builder.Default
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId = UUID.randomUUID();

    @Column(name = "amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "source_country", nullable = false, length = 2)
    private String sourceCountry;

    @Column(name = "payment_rail", nullable = false, length = 30)
    private String paymentRail;

    @Column(name = "psp", nullable = false, length = 50)
    private String pspName;

    @Column(name = "psp_id", length = 50)
    private String pspId;

    @Column(name = "psp_reference", length = 200)
    private String pspReference;

    @Column(name = "sender_account_hash", length = 128)
    private String senderAccountHash;

    @Column(name = "sender_bank_code", length = 50)
    private String senderBankCode;

    @Column(name = "sender_account_type", length = 30)
    private String senderAccountType;

    @Column(name = "sender_country", length = 2)
    private String senderCountry;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CollectionStatus status;

    @Column(name = "settled_amount", precision = 20, scale = 8)
    private BigDecimal settledAmount;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
