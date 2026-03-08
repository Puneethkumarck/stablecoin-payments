package com.stablecoin.payments.orchestrator.infrastructure.persistence.entity;

import com.stablecoin.payments.orchestrator.domain.model.PaymentState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    List<PaymentEntity> findBySenderIdAndState(UUID senderId, PaymentState state);
}
