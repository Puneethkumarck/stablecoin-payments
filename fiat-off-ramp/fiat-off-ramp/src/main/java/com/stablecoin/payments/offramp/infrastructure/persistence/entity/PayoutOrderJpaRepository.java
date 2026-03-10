package com.stablecoin.payments.offramp.infrastructure.persistence.entity;

import com.stablecoin.payments.offramp.domain.model.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutOrderJpaRepository extends JpaRepository<PayoutOrderEntity, UUID> {

    Optional<PayoutOrderEntity> findByPaymentId(UUID paymentId);

    Optional<PayoutOrderEntity> findByPartnerReference(String partnerReference);

    List<PayoutOrderEntity> findByStatus(PayoutStatus status);

    List<PayoutOrderEntity> findByRecipientId(UUID recipientId);
}
