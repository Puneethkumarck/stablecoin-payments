package com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository;

import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByKeyHash(String keyHash);

    List<ApiKeyEntity> findByMerchantIdAndActiveTrue(UUID merchantId);

    @Modifying
    @Query("UPDATE ApiKeyEntity k SET k.active = false, k.revokedAt = CURRENT_TIMESTAMP, " +
            "k.updatedAt = CURRENT_TIMESTAMP WHERE k.merchantId = :merchantId AND k.active = true")
    int deactivateAllByMerchantId(@Param("merchantId") UUID merchantId);
}
