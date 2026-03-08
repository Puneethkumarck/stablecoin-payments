package com.stablecoin.payments.onramp.infrastructure.persistence.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CollectionOrderJpaRepository extends JpaRepository<CollectionOrderEntity, UUID> {

    Optional<CollectionOrderEntity> findByPaymentId(UUID paymentId);
}
