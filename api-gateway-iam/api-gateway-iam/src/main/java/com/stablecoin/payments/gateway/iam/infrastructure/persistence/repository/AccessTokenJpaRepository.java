package com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository;

import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.AccessTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AccessTokenJpaRepository extends JpaRepository<AccessTokenEntity, UUID> {

    @Modifying
    @Query("UPDATE AccessTokenEntity t SET t.revoked = true, t.revokedAt = CURRENT_TIMESTAMP " +
            "WHERE t.merchantId = :merchantId AND t.revoked = false")
    int revokeAllByMerchantId(@Param("merchantId") UUID merchantId);

    @Modifying
    @Query("DELETE FROM AccessTokenEntity t WHERE t.revoked = true AND t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
