package com.stablecoin.payments.offramp.infrastructure.persistence.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StablecoinRedemptionJpaRepository extends JpaRepository<StablecoinRedemptionEntity, UUID> {

    Optional<StablecoinRedemptionEntity> findByPayoutId(UUID payoutId);
}
