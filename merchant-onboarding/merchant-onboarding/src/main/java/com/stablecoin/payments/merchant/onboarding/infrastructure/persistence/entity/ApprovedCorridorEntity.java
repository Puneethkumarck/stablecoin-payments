package com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity;

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
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "approved_corridors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovedCorridorEntity {

    @Id
    @Column(name = "corridor_id", updatable = false, nullable = false)
    private UUID corridorId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "source_country", nullable = false, length = 2)
    private String sourceCountry;

    @Column(name = "target_country", nullable = false, length = 2)
    private String targetCountry;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "currencies", columnDefinition = "jsonb", nullable = false)
    private List<String> currencies;

    @Column(name = "max_amount_usd", nullable = false, precision = 20, scale = 2)
    private BigDecimal maxAmountUsd;

    @Column(name = "approved_by", nullable = false)
    private UUID approvedBy;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;
}
