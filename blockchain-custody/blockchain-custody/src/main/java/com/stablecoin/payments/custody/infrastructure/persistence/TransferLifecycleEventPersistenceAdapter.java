package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.domain.model.TransferLifecycleEvent;
import com.stablecoin.payments.custody.domain.port.TransferLifecycleEventRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.TransferLifecycleEventJpaRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.mapper.TransferLifecycleEventPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TransferLifecycleEventPersistenceAdapter implements TransferLifecycleEventRepository {

    private final TransferLifecycleEventJpaRepository jpa;
    private final TransferLifecycleEventPersistenceMapper mapper;

    @Override
    public TransferLifecycleEvent save(TransferLifecycleEvent event) {
        return mapper.toDomain(jpa.save(mapper.toEntity(event)));
    }

    @Override
    public List<TransferLifecycleEvent> findByTransferId(UUID transferId) {
        return jpa.findByTransferId(transferId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
