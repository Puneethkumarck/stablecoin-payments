package com.stablecoin.payments.custody.infrastructure.persistence.entity;

import com.stablecoin.payments.custody.domain.model.TransferStatus;
import com.stablecoin.payments.custody.domain.model.TransferType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChainTransferJpaRepository extends JpaRepository<ChainTransferEntity, UUID> {

    Optional<ChainTransferEntity> findByPaymentIdAndTransferType(UUID paymentId, TransferType type);

    List<ChainTransferEntity> findByStatus(TransferStatus status);
}
