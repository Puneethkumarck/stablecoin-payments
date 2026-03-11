package com.stablecoin.payments.ledger.infrastructure.persistence.adapter;

import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.port.ReconciliationLegRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.mapper.ReconciliationLegPersistenceMapper;
import com.stablecoin.payments.ledger.infrastructure.persistence.repository.ReconciliationLegJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReconciliationLegPersistenceAdapter implements ReconciliationLegRepository {

    private final ReconciliationLegJpaRepository jpa;
    private final ReconciliationLegPersistenceMapper mapper;

    @Override
    public ReconciliationLeg save(ReconciliationLeg leg) {
        return mapper.toDomain(jpa.save(mapper.toEntity(leg)));
    }

    @Override
    public List<ReconciliationLeg> findByRecId(UUID recId) {
        return jpa.findByRecId(recId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
