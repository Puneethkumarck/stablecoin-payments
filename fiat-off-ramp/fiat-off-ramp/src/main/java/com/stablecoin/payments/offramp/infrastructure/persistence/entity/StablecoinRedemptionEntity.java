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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stablecoin_redemptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StablecoinRedemptionEntity {

    @Id
    @Column(name = "redemption_id", updatable = false, nullable = false)
    private UUID redemptionId;

    @Column(name = "payout_id", nullable = false)
    private UUID payoutId;

    @Column(name = "stablecoin", nullable = false, length = 20)
    private String stablecoin;

    @Column(name = "redeemed_amount", nullable = false, precision = 30, scale = 8)
    private BigDecimal redeemedAmount;

    @Column(name = "fiat_received", nullable = false, precision = 20, scale = 8)
    private BigDecimal fiatReceived;

    @Column(name = "fiat_currency", nullable = false, length = 3)
    private String fiatCurrency;

    @Column(name = "partner", nullable = false, length = 100)
    private String partner;

    @Column(name = "partner_reference", nullable = false, length = 200)
    private String partnerReference;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;
}
