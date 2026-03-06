package com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.EntityType;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.KybStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.MerchantStatus;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RateLimitTier;
import com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core.RiskTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "merchants",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_merchants_reg_country",
                columnNames = {"registration_number", "registration_country"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantEntity {

    @Id
    @Column(name = "merchant_id", updatable = false, nullable = false)
    private UUID merchantId;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trading_name")
    private String tradingName;

    @Column(name = "registration_number", nullable = false)
    private String registrationNumber;

    @Column(name = "registration_country", nullable = false, length = 2)
    private String registrationCountry;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "primary_currency", nullable = false, length = 3)
    private String primaryCurrency;

    @Column(name = "primary_contact_email")
    private String primaryContactEmail;

    @Column(name = "primary_contact_name")
    private String primaryContactName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private MerchantStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kyb_status", nullable = false)
    private KybStatus kybStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "risk_tier")
    private RiskTier riskTier;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "rate_limit_tier", nullable = false)
    private RateLimitTier rateLimitTier;

    @Column(name = "onboarded_by")
    private UUID onboardedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "registered_address", columnDefinition = "jsonb")
    private AddressJson registeredAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "beneficial_owners", columnDefinition = "jsonb")
    private List<BeneficialOwnerJson> beneficialOwners;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_corridors", columnDefinition = "jsonb")
    private List<String> requestedCorridors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_scopes", columnDefinition = "jsonb")
    private List<String> allowedScopes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public record AddressJson(
            String streetLine1,
            String streetLine2,
            String city,
            String stateProvince,
            String postcode,
            String country
    ) {}

    public record BeneficialOwnerJson(
            String fullName,
            String dateOfBirth,
            String nationality,
            String ownershipPct,
            boolean isPoliticallyExposed,
            String nationalIdRef
    ) {}
}
