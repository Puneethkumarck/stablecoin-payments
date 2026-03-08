package com.stablecoin.payments.fx.infrastructure.persistence;

import com.stablecoin.payments.fx.domain.model.FxRateLock;
import com.stablecoin.payments.fx.domain.model.FxRateLockStatus;
import com.stablecoin.payments.fx.domain.port.FxRateLockRepository;
import com.stablecoin.payments.fx.infrastructure.persistence.entity.FxRateLockJpaRepository;
import com.stablecoin.payments.fx.infrastructure.persistence.mapper.FxRateLockEntityUpdater;
import com.stablecoin.payments.fx.infrastructure.persistence.mapper.FxRateLockPersistenceMapper;
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
public class FxRateLockPersistenceAdapter implements FxRateLockRepository {

    private final FxRateLockJpaRepository jpa;
    private final FxRateLockPersistenceMapper mapper;
    private final FxRateLockEntityUpdater updater;

    @Override
    public FxRateLock save(FxRateLock lock) {
        var existing = jpa.findById(lock.lockId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), lock);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(lock)));
    }

    @Override
    public Optional<FxRateLock> findById(UUID lockId) {
        return jpa.findById(lockId).map(mapper::toDomain);
    }

    @Override
    public Optional<FxRateLock> findByPaymentId(UUID paymentId) {
        return jpa.findByPaymentId(paymentId).map(mapper::toDomain);
    }

    @Override
    public List<FxRateLock> findActiveLocksExpiredBefore(Instant cutoff) {
        return jpa.findByStatusAndExpiresAtBefore(FxRateLockStatus.ACTIVE.name(), cutoff)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
