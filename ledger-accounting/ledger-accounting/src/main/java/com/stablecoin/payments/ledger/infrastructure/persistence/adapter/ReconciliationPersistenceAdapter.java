package com.stablecoin.payments.ledger.infrastructure.persistence.adapter;

import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.model.ReconciliationStatus;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.mapper.ReconciliationLegPersistenceMapper;
import com.stablecoin.payments.ledger.infrastructure.persistence.mapper.ReconciliationRecordPersistenceMapper;
import com.stablecoin.payments.ledger.infrastructure.persistence.repository.ReconciliationLegJpaRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.repository.ReconciliationRecordJpaRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.updater.ReconciliationRecordEntityUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReconciliationPersistenceAdapter implements ReconciliationRepository {

    private final ReconciliationRecordJpaRepository recordJpa;
    private final ReconciliationLegJpaRepository legJpa;
    private final ReconciliationRecordPersistenceMapper recordMapper;
    private final ReconciliationLegPersistenceMapper legMapper;
    private final ReconciliationRecordEntityUpdater updater;

    @Override
    public ReconciliationRecord save(ReconciliationRecord record) {
        var existing = recordJpa.findById(record.recId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), record);
            var savedEntity = recordJpa.save(existing.get());
            List<ReconciliationLeg> legs = loadLegs(record.recId());
            return recordMapper.toDomain(savedEntity, legs);
        }
        var savedEntity = recordJpa.save(recordMapper.toEntity(record));
        List<ReconciliationLeg> legs = loadLegs(record.recId());
        return recordMapper.toDomain(savedEntity, legs);
    }

    @Override
    public Optional<ReconciliationRecord> findById(UUID recId) {
        return recordJpa.findById(recId)
                .map(entity -> {
                    List<ReconciliationLeg> legs = loadLegs(recId);
                    return recordMapper.toDomain(entity, legs);
                });
    }

    @Override
    public Optional<ReconciliationRecord> findByPaymentId(UUID paymentId) {
        return recordJpa.findByPaymentId(paymentId)
                .map(entity -> {
                    List<ReconciliationLeg> legs = loadLegs(entity.getRecId());
                    return recordMapper.toDomain(entity, legs);
                });
    }

    @Override
    public List<ReconciliationRecord> findByStatus(ReconciliationStatus status) {
        return recordJpa.findByStatus(status.name()).stream()
                .map(entity -> {
                    List<ReconciliationLeg> legs = loadLegs(entity.getRecId());
                    return recordMapper.toDomain(entity, legs);
                })
                .toList();
    }

    private List<ReconciliationLeg> loadLegs(UUID recId) {
        return legJpa.findByRecId(recId).stream()
                .map(legMapper::toDomain)
                .toList();
    }
}
