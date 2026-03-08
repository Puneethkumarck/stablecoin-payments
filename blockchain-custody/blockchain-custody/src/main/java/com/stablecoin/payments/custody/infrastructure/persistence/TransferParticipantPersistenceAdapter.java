package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.domain.model.TransferParticipant;
import com.stablecoin.payments.custody.domain.port.TransferParticipantRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.TransferParticipantJpaRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.mapper.TransferParticipantPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TransferParticipantPersistenceAdapter implements TransferParticipantRepository {

    private final TransferParticipantJpaRepository jpa;
    private final TransferParticipantPersistenceMapper mapper;

    @Override
    public TransferParticipant save(TransferParticipant participant) {
        return mapper.toDomain(jpa.save(mapper.toEntity(participant)));
    }

    @Override
    public List<TransferParticipant> findByTransferId(UUID transferId) {
        return jpa.findByTransferId(transferId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
