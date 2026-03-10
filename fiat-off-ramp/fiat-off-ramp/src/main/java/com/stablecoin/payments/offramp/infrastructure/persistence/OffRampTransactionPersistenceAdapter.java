package com.stablecoin.payments.offramp.infrastructure.persistence;

import com.stablecoin.payments.offramp.domain.model.OffRampTransaction;
import com.stablecoin.payments.offramp.domain.port.OffRampTransactionRepository;
import com.stablecoin.payments.offramp.infrastructure.persistence.entity.OffRampTransactionJpaRepository;
import com.stablecoin.payments.offramp.infrastructure.persistence.mapper.OffRampTransactionPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OffRampTransactionPersistenceAdapter implements OffRampTransactionRepository {

    private final OffRampTransactionJpaRepository jpa;
    private final OffRampTransactionPersistenceMapper mapper;

    @Override
    public OffRampTransaction save(OffRampTransaction transaction) {
        return mapper.toDomain(jpa.save(mapper.toEntity(transaction)));
    }

    @Override
    public Optional<OffRampTransaction> findById(UUID offRampTxnId) {
        return jpa.findById(offRampTxnId).map(mapper::toDomain);
    }

    @Override
    public List<OffRampTransaction> findByPayoutId(UUID payoutId) {
        return jpa.findByPayoutId(payoutId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
