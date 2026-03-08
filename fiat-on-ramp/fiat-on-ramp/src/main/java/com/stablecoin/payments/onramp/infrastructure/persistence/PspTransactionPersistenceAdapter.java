package com.stablecoin.payments.onramp.infrastructure.persistence;

import com.stablecoin.payments.onramp.domain.model.PspTransaction;
import com.stablecoin.payments.onramp.domain.port.PspTransactionRepository;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.PspTransactionJpaRepository;
import com.stablecoin.payments.onramp.infrastructure.persistence.mapper.PspTransactionPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PspTransactionPersistenceAdapter implements PspTransactionRepository {

    private final PspTransactionJpaRepository jpa;
    private final PspTransactionPersistenceMapper mapper;

    @Override
    public PspTransaction save(PspTransaction transaction) {
        return mapper.toDomain(jpa.save(mapper.toEntity(transaction)));
    }

    @Override
    public List<PspTransaction> findByCollectionId(UUID collectionId) {
        return jpa.findByCollectionId(collectionId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
