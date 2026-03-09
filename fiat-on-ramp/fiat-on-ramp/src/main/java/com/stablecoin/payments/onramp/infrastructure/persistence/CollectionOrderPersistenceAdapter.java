package com.stablecoin.payments.onramp.infrastructure.persistence;

import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.CollectionStatus;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.CollectionOrderJpaRepository;
import com.stablecoin.payments.onramp.infrastructure.persistence.mapper.CollectionOrderEntityUpdater;
import com.stablecoin.payments.onramp.infrastructure.persistence.mapper.CollectionOrderPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CollectionOrderPersistenceAdapter implements CollectionOrderRepository {

    private final CollectionOrderJpaRepository jpa;
    private final CollectionOrderPersistenceMapper mapper;
    private final CollectionOrderEntityUpdater updater;

    @Override
    public CollectionOrder save(CollectionOrder order) {
        var existing = jpa.findById(order.collectionId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), order);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(order)));
    }

    @Override
    public Optional<CollectionOrder> findById(UUID collectionId) {
        return jpa.findById(collectionId).map(mapper::toDomain);
    }

    @Override
    public Optional<CollectionOrder> findByPaymentId(UUID paymentId) {
        return jpa.findByPaymentId(paymentId).map(mapper::toDomain);
    }

    @Override
    public Optional<CollectionOrder> findByPspReference(String pspReference) {
        return jpa.findByPspReference(pspReference).map(mapper::toDomain);
    }

    @Override
    public List<CollectionOrder> findByStatusAndNotReconciled(CollectionStatus status) {
        return jpa.findByStatusAndNotReconciled(status).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<CollectionOrder> findExpiredByStatus(CollectionStatus status, Instant before) {
        return jpa.findExpiredByStatus(status, before).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
