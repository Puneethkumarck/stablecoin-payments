package com.stablecoin.payments.ledger.infrastructure.persistence.adapter;

import com.stablecoin.payments.ledger.domain.model.AuditEvent;
import com.stablecoin.payments.ledger.domain.port.AuditEventRepository;
import com.stablecoin.payments.ledger.infrastructure.persistence.mapper.AuditEventPersistenceMapper;
import com.stablecoin.payments.ledger.infrastructure.persistence.repository.AuditEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AuditEventPersistenceAdapter implements AuditEventRepository {

    private final AuditEventJpaRepository jpa;
    private final AuditEventPersistenceMapper mapper;

    @Override
    public AuditEvent save(AuditEvent event) {
        return mapper.toDomain(jpa.save(mapper.toEntity(event)));
    }

    @Override
    public List<AuditEvent> findByPaymentId(UUID paymentId) {
        return jpa.findByPaymentIdOrderByOccurredAtDesc(paymentId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<AuditEvent> findByCorrelationId(UUID correlationId) {
        return jpa.findByCorrelationId(correlationId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
