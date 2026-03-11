package com.stablecoin.payments.ledger.infrastructure.persistence.mapper;

import com.stablecoin.payments.ledger.domain.model.AuditEvent;
import com.stablecoin.payments.ledger.infrastructure.persistence.entity.AuditEventEntity;
import org.mapstruct.Mapper;

@Mapper
public interface AuditEventPersistenceMapper {

    default AuditEventEntity toEntity(AuditEvent event) {
        if (event == null) return null;
        return AuditEventEntity.builder()
                .auditId(event.auditId())
                .correlationId(event.correlationId())
                .paymentId(event.paymentId())
                .serviceName(event.serviceName())
                .eventType(event.eventType())
                .eventPayload(event.eventPayload())
                .actor(event.actor())
                .occurredAt(event.occurredAt())
                .receivedAt(event.receivedAt())
                .build();
    }

    default AuditEvent toDomain(AuditEventEntity entity) {
        if (entity == null) return null;
        return new AuditEvent(
                entity.getAuditId(),
                entity.getCorrelationId(),
                entity.getPaymentId(),
                entity.getServiceName(),
                entity.getEventType(),
                entity.getEventPayload(),
                entity.getActor(),
                entity.getOccurredAt(),
                entity.getReceivedAt()
        );
    }
}
