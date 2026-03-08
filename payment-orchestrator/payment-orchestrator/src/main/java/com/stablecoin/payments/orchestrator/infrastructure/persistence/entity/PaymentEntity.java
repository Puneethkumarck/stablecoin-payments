package com.stablecoin.payments.orchestrator.infrastructure.persistence.entity;

import com.stablecoin.payments.orchestrator.domain.model.PaymentState;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @Column(name = "payment_id", updatable = false, nullable = false)
    private UUID paymentId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private PaymentState state;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "sender_account_id")
    private UUID senderAccountId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "recipient_account_id")
    private UUID recipientAccountId;

    @Column(name = "source_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal sourceAmount;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(name = "target_amount", precision = 20, scale = 8)
    private BigDecimal targetAmount;

    @Column(name = "fx_quote_id")
    private UUID fxQuoteId;

    @Column(name = "locked_fx_rate", precision = 20, scale = 10)
    private BigDecimal lockedFxRate;

    @Column(name = "fx_rate_locked_at")
    private Instant fxRateLockedAt;

    @Column(name = "fx_rate_expires_at")
    private Instant fxRateExpiresAt;

    @Column(name = "fx_rate_provider", length = 100)
    private String fxRateProvider;

    @Column(name = "source_country", nullable = false, length = 2)
    private String sourceCountry;

    @Column(name = "target_country", nullable = false, length = 2)
    private String targetCountry;

    @Column(name = "chain_id", length = 20)
    private String chainId;

    @Column(name = "tx_hash", length = 128)
    private String txHash;

    @Column(name = "purpose_code", length = 50)
    private String purposeCode;

    @Column(name = "reference", length = 128)
    private String reference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
