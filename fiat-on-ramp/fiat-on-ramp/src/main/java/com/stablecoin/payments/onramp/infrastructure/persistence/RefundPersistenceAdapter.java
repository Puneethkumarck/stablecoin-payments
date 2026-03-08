package com.stablecoin.payments.onramp.infrastructure.persistence;

import com.stablecoin.payments.onramp.domain.model.Refund;
import com.stablecoin.payments.onramp.domain.port.RefundRepository;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.RefundJpaRepository;
import com.stablecoin.payments.onramp.infrastructure.persistence.mapper.RefundEntityUpdater;
import com.stablecoin.payments.onramp.infrastructure.persistence.mapper.RefundPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RefundPersistenceAdapter implements RefundRepository {

    private final RefundJpaRepository jpa;
    private final RefundPersistenceMapper mapper;
    private final RefundEntityUpdater updater;

    @Override
    public Refund save(Refund refund) {
        var existing = jpa.findById(refund.refundId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), refund);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(refund)));
    }

    @Override
    public Optional<Refund> findById(UUID refundId) {
        return jpa.findById(refundId).map(mapper::toDomain);
    }

    @Override
    public List<Refund> findByCollectionId(UUID collectionId) {
        return jpa.findByCollectionId(collectionId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
