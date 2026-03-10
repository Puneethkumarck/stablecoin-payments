package com.stablecoin.payments.offramp.infrastructure.persistence;

import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.PayoutStatus;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import com.stablecoin.payments.offramp.infrastructure.persistence.entity.PayoutOrderJpaRepository;
import com.stablecoin.payments.offramp.infrastructure.persistence.mapper.PayoutOrderEntityUpdater;
import com.stablecoin.payments.offramp.infrastructure.persistence.mapper.PayoutOrderPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PayoutOrderPersistenceAdapter implements PayoutOrderRepository {

    private final PayoutOrderJpaRepository jpa;
    private final PayoutOrderPersistenceMapper mapper;
    private final PayoutOrderEntityUpdater updater;

    @Override
    public PayoutOrder save(PayoutOrder order) {
        var existing = jpa.findById(order.payoutId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), order);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(order)));
    }

    @Override
    public Optional<PayoutOrder> findById(UUID payoutId) {
        return jpa.findById(payoutId).map(mapper::toDomain);
    }

    @Override
    public Optional<PayoutOrder> findByPaymentId(UUID paymentId) {
        return jpa.findByPaymentId(paymentId).map(mapper::toDomain);
    }

    @Override
    public Optional<PayoutOrder> findByPartnerReference(String partnerReference) {
        return jpa.findByPartnerReference(partnerReference).map(mapper::toDomain);
    }

    @Override
    public List<PayoutOrder> findByStatus(PayoutStatus status) {
        return jpa.findByStatus(status).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<PayoutOrder> findByRecipientId(UUID recipientId) {
        return jpa.findByRecipientId(recipientId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
