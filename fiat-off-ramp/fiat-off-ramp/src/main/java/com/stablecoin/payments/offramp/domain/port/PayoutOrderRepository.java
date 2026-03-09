package com.stablecoin.payments.offramp.domain.port;

import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.PayoutStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutOrderRepository {

    PayoutOrder save(PayoutOrder order);

    Optional<PayoutOrder> findById(UUID payoutId);

    Optional<PayoutOrder> findByPaymentId(UUID paymentId);

    Optional<PayoutOrder> findByPartnerReference(String partnerReference);

    List<PayoutOrder> findByStatus(PayoutStatus status);

    List<PayoutOrder> findByRecipientId(UUID recipientId);
}
