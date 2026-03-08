package com.stablecoin.payments.custody.infrastructure.persistence.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransferParticipantJpaRepository extends JpaRepository<TransferParticipantEntity, UUID> {

    List<TransferParticipantEntity> findByTransferId(UUID transferId);
}
