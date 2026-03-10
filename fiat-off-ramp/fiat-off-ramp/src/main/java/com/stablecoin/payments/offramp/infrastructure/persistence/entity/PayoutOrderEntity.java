package com.stablecoin.payments.offramp.infrastructure.persistence.entity;

import com.stablecoin.payments.offramp.domain.model.PaymentRail;
import com.stablecoin.payments.offramp.domain.model.PayoutStatus;
import com.stablecoin.payments.offramp.domain.model.PayoutType;
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
@Table(name = "payout_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutOrderEntity {

    @Id
    @Column(name = "payout_id", updatable = false, nullable = false)
    private UUID payoutId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_type", nullable = false, length = 20)
    private PayoutType payoutType;

    @Column(name = "stablecoin", nullable = false, length = 20)
    private String stablecoin;

    @Column(name = "redeemed_amount", nullable = false, precision = 30, scale = 8)
    private BigDecimal redeemedAmount;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(name = "fiat_amount", precision = 20, scale = 8)
    private BigDecimal fiatAmount;

    @Column(name = "applied_fx_rate", nullable = false, precision = 20, scale = 10)
    private BigDecimal appliedFxRate;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "recipient_account_hash", nullable = false, length = 64)
    private String recipientAccountHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_rail", nullable = false, length = 30)
    private PaymentRail paymentRail;

    @Column(name = "off_ramp_partner_id", nullable = false, length = 50)
    private String offRampPartnerId;

    @Column(name = "off_ramp_partner_name", nullable = false, length = 100)
    private String offRampPartnerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private PayoutStatus status;

    @Column(name = "partner_reference", length = 200)
    private String partnerReference;

    @Column(name = "partner_settled_at")
    private Instant partnerSettledAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    // -- BankAccount VO flattened --
    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_code", length = 50)
    private String bankCode;

    @Column(name = "bank_account_type", length = 20)
    private String bankAccountType;

    @Column(name = "bank_country", length = 2)
    private String bankCountry;

    // -- MobileMoneyAccount VO flattened --
    @Column(name = "mobile_money_provider", length = 30)
    private String mobileMoneyProvider;

    @Column(name = "mobile_money_phone", length = 30)
    private String mobileMoneyPhone;

    @Column(name = "mobile_money_country", length = 2)
    private String mobileMoneyCountry;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
