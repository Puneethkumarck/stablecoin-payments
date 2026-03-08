package com.stablecoin.payments.custody.infrastructure.persistence.mapper;

import com.stablecoin.payments.custody.domain.model.TransferParticipant;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.TransferParticipantEntity;
import org.mapstruct.Mapper;

@Mapper
public interface TransferParticipantPersistenceMapper {

    default TransferParticipantEntity toEntity(TransferParticipant participant) {
        if (participant == null) {
            return null;
        }
        return TransferParticipantEntity.builder()
                .participantId(participant.participantId())
                .transferId(participant.transferId())
                .participantType(participant.participantType())
                .address(participant.address())
                .walletId(participant.walletId())
                .amount(participant.amount())
                .assetCode(participant.assetCode())
                .build();
    }

    default TransferParticipant toDomain(TransferParticipantEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TransferParticipant(
                entity.getParticipantId(),
                entity.getTransferId(),
                entity.getParticipantType(),
                entity.getAddress(),
                entity.getWalletId(),
                entity.getAmount(),
                entity.getAssetCode()
        );
    }
}
