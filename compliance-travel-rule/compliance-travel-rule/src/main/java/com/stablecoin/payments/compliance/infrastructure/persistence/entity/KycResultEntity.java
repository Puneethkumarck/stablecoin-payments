package com.stablecoin.payments.compliance.infrastructure.persistence.entity;

import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.KycTier;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycResultEntity {

    @Id
    @Column(name = "kyc_result_id", updatable = false, nullable = false)
    private UUID kycResultId;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_kyc_tier", nullable = false, length = 20)
    private KycTier senderKycTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_status", nullable = false, length = 20)
    private KycStatus senderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_status", nullable = false, length = 20)
    private KycStatus recipientStatus;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_ref", nullable = false, length = 200)
    private String providerRef;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", nullable = false, columnDefinition = "jsonb")
    private String rawResponse = "{}";

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
}
