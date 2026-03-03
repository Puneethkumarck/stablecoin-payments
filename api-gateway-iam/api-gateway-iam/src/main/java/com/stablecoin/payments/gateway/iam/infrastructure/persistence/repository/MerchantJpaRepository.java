package com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository;

import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantJpaRepository extends JpaRepository<MerchantEntity, UUID> {

    Optional<MerchantEntity> findByExternalId(UUID externalId);

    boolean existsByExternalId(UUID externalId);
}
