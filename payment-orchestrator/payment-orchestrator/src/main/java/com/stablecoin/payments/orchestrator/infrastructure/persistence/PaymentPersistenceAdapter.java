package com.stablecoin.payments.orchestrator.infrastructure.persistence;

import com.stablecoin.payments.orchestrator.domain.model.Payment;
import com.stablecoin.payments.orchestrator.domain.model.PaymentState;
import com.stablecoin.payments.orchestrator.domain.port.PaymentRepository;
import com.stablecoin.payments.orchestrator.infrastructure.persistence.entity.PaymentJpaRepository;
import com.stablecoin.payments.orchestrator.infrastructure.persistence.mapper.PaymentEntityUpdater;
import com.stablecoin.payments.orchestrator.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpa;
    private final PaymentPersistenceMapper mapper;
    private final PaymentEntityUpdater updater;

    @Override
    public Payment save(Payment payment) {
        var existing = jpa.findById(payment.paymentId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), payment);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(payment)));
    }

    @Override
    public Optional<Payment> findById(UUID paymentId) {
        return jpa.findById(paymentId).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain);
    }

    @Override
    public List<Payment> findBySenderIdAndState(UUID senderId, PaymentState state) {
        return jpa.findBySenderIdAndState(senderId, state).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
