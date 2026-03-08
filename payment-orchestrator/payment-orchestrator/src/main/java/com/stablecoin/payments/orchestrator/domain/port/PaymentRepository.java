package com.stablecoin.payments.orchestrator.domain.port;

import com.stablecoin.payments.orchestrator.domain.model.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
