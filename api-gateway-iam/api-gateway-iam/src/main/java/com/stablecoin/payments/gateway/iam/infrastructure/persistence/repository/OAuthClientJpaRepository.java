package com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository;

import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.OAuthClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OAuthClientJpaRepository extends JpaRepository<OAuthClientEntity, UUID> {

    Optional<OAuthClientEntity> findByClientIdAndActiveTrue(UUID clientId);

    List<OAuthClientEntity> findByMerchantId(UUID merchantId);

    @Modifying
    @Query("UPDATE OAuthClientEntity c SET c.active = false, c.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE c.merchantId = :merchantId AND c.active = true")
    int deactivateAllByMerchantId(@Param("merchantId") UUID merchantId);
}
