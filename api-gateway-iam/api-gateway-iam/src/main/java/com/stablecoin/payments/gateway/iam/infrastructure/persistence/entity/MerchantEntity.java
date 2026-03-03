package com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantEntity {

    @Id
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "external_id", nullable = false, unique = true)
    private UUID externalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "country", nullable = false, length = 3)
    private String country;

    @Column(name = "scopes", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] scopes;

    @Column(name = "corridors", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String corridors;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "kyb_status", nullable = false, length = 20)
    private String kybStatus;

    @Column(name = "rate_limit_tier", nullable = false, length = 20)
    private String rateLimitTier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
