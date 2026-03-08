package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.TransferStatus;
import com.stablecoin.payments.custody.domain.model.TransferType;
import com.stablecoin.payments.custody.domain.port.ChainTransferRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.ChainTransferJpaRepository;
import com.stablecoin.payments.custody.infrastructure.persistence.mapper.ChainTransferEntityUpdater;
import com.stablecoin.payments.custody.infrastructure.persistence.mapper.ChainTransferPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ChainTransferPersistenceAdapter implements ChainTransferRepository {

    private final ChainTransferJpaRepository jpa;
    private final ChainTransferPersistenceMapper mapper;
    private final ChainTransferEntityUpdater updater;

    @Override
    public ChainTransfer save(ChainTransfer transfer) {
        var existing = jpa.findById(transfer.transferId());
        if (existing.isPresent()) {
            updater.updateEntity(existing.get(), transfer);
            return mapper.toDomain(jpa.save(existing.get()));
        }
        return mapper.toDomain(jpa.save(mapper.toEntity(transfer)));
    }

    @Override
    public Optional<ChainTransfer> findById(UUID transferId) {
        return jpa.findById(transferId).map(mapper::toDomain);
    }

    @Override
    public Optional<ChainTransfer> findByPaymentIdAndType(UUID paymentId, TransferType type) {
        return jpa.findByPaymentIdAndTransferType(paymentId, type).map(mapper::toDomain);
    }

    @Override
    public List<ChainTransfer> findByStatus(TransferStatus status) {
        return jpa.findByStatus(status).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
