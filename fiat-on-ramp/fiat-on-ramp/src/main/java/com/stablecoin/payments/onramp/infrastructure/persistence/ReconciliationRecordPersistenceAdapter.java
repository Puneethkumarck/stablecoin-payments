package com.stablecoin.payments.onramp.infrastructure.persistence;

import com.stablecoin.payments.onramp.domain.model.ReconciliationRecord;
import com.stablecoin.payments.onramp.domain.port.ReconciliationRecordRepository;
import com.stablecoin.payments.onramp.infrastructure.persistence.entity.ReconciliationRecordJpaRepository;
import com.stablecoin.payments.onramp.infrastructure.persistence.mapper.ReconciliationRecordPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ReconciliationRecordPersistenceAdapter implements ReconciliationRecordRepository {

    private final ReconciliationRecordJpaRepository jpa;
    private final ReconciliationRecordPersistenceMapper mapper;

    @Override
    public ReconciliationRecord save(ReconciliationRecord record) {
        return mapper.toDomain(jpa.save(mapper.toEntity(record)));
    }

    @Override
    public Optional<ReconciliationRecord> findByCollectionId(UUID collectionId) {
        return jpa.findByCollectionId(collectionId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCollectionId(UUID collectionId) {
        return jpa.existsByCollectionId(collectionId);
    }
}
