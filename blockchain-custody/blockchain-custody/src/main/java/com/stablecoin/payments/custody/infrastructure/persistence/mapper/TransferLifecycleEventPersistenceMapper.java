package com.stablecoin.payments.custody.infrastructure.persistence.mapper;

import com.stablecoin.payments.custody.domain.model.TransferLifecycleEvent;
import com.stablecoin.payments.custody.infrastructure.persistence.entity.TransferLifecycleEventEntity;
import org.mapstruct.Mapper;

@Mapper
public interface TransferLifecycleEventPersistenceMapper {

    default TransferLifecycleEventEntity toEntity(TransferLifecycleEvent event) {
        if (event == null) {
            return null;
        }
        return TransferLifecycleEventEntity.builder()
                .eventId(event.eventId())
                .transferId(event.transferId())
                .state(event.state())
                .participantType(event.participantType())
                .address(event.address())
                .occurredAt(event.occurredAt())
                .build();
    }

    default TransferLifecycleEvent toDomain(TransferLifecycleEventEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TransferLifecycleEvent(
                entity.getEventId(),
                entity.getTransferId(),
                entity.getState(),
                entity.getParticipantType(),
                entity.getAddress(),
                entity.getOccurredAt()
        );
    }
}
