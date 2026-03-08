package com.stablecoin.payments.orchestrator.domain.port;

import com.stablecoin.payments.orchestrator.domain.model.Payment;
import com.stablecoin.payments.orchestrator.domain.model.PaymentState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findBySenderIdAndState(UUID senderId, PaymentState state);
}
