package com.stablecoin.payments.orchestrator.infrastructure.persistence;

import com.stablecoin.payments.orchestrator.AbstractIntegrationTest;
import com.stablecoin.payments.orchestrator.infrastructure.persistence.entity.PaymentAuditLogEntity;
import com.stablecoin.payments.orchestrator.infrastructure.persistence.entity.PaymentAuditLogJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentAuditLogRepository IT")
class PaymentAuditLogRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private PaymentAuditLogJpaRepository auditLogRepository;

    // ── Append-only insert ──────────────────────────────────────────────

    @Test
    @DisplayName("should insert audit log entry and retrieve by id")
    void shouldInsertAuditLogEntryAndRetrieveById() {
        var paymentId = UUID.randomUUID();
        var logEntry = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId)
                .fromState(null)
                .toState("INITIATED")
                .triggeredBy("SYSTEM")
                .actor("payment-orchestrator")
                .metadata(Map.of("source", "api"))
                .occurredAt(Instant.now())
                .build();

        var saved = auditLogRepository.save(logEntry);

        var expected = PaymentAuditLogEntity.builder()
                .logId(saved.getLogId())
                .paymentId(paymentId)
                .fromState(null)
                .toState("INITIATED")
                .triggeredBy("SYSTEM")
                .actor("payment-orchestrator")
                .metadata(Map.of("source", "api"))
                .occurredAt(saved.getOccurredAt())
                .build();

        assertThat(auditLogRepository.findById(saved.getLogId())).isPresent().get()
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should insert multiple audit log entries for same payment")
    void shouldInsertMultipleAuditLogEntriesForSamePayment() {
        var paymentId = UUID.randomUUID();
        var now = Instant.now();

        var entry1 = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId)
                .fromState(null)
                .toState("INITIATED")
                .triggeredBy("INITIATE")
                .actor("system")
                .metadata(Map.of())
                .occurredAt(now.minusSeconds(30))
                .build();

        var entry2 = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId)
                .fromState("INITIATED")
                .toState("COMPLIANCE_CHECK")
                .triggeredBy("START_COMPLIANCE")
                .actor("system")
                .metadata(Map.of())
                .occurredAt(now.minusSeconds(20))
                .build();

        var entry3 = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId)
                .fromState("COMPLIANCE_CHECK")
                .toState("FX_LOCKED")
                .triggeredBy("COMPLIANCE_PASSED")
                .actor("compliance-service")
                .metadata(Map.of("provider", "ecb"))
                .occurredAt(now.minusSeconds(10))
                .build();

        auditLogRepository.save(entry1);
        auditLogRepository.save(entry2);
        auditLogRepository.save(entry3);

        var results = auditLogRepository.findByPaymentIdOrderByOccurredAtDesc(paymentId);
        assertThat(results).hasSize(3);
    }

    // ── Query by payment_id ordered by occurred_at desc ─────────────────

    @Test
    @DisplayName("should return audit log entries ordered by occurred_at descending")
    void shouldReturnAuditLogEntriesOrderedByOccurredAtDesc() {
        var paymentId = UUID.randomUUID();
        var now = Instant.now();

        var earliest = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId)
                .fromState(null)
                .toState("INITIATED")
                .triggeredBy("INITIATE")
                .actor("system")
                .metadata(Map.of())
                .occurredAt(now.minusSeconds(60))
                .build();

        var middle = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId)
                .fromState("INITIATED")
                .toState("COMPLIANCE_CHECK")
                .triggeredBy("START_COMPLIANCE")
                .actor("system")
                .metadata(Map.of())
                .occurredAt(now.minusSeconds(30))
                .build();

        var latest = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId)
                .fromState("COMPLIANCE_CHECK")
                .toState("FX_LOCKED")
                .triggeredBy("COMPLIANCE_PASSED")
                .actor("compliance-service")
                .metadata(Map.of())
                .occurredAt(now)
                .build();

        // Insert in non-chronological order to verify sorting
        auditLogRepository.save(middle);
        auditLogRepository.save(latest);
        auditLogRepository.save(earliest);

        var results = auditLogRepository.findByPaymentIdOrderByOccurredAtDesc(paymentId);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getToState()).isEqualTo("FX_LOCKED");
        assertThat(results.get(1).getToState()).isEqualTo("COMPLIANCE_CHECK");
        assertThat(results.get(2).getToState()).isEqualTo("INITIATED");
    }

    @Test
    @DisplayName("should return empty list when no audit logs exist for payment")
    void shouldReturnEmptyListWhenNoAuditLogsExistForPayment() {
        var results = auditLogRepository.findByPaymentIdOrderByOccurredAtDesc(UUID.randomUUID());
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should not return audit logs for different payment id")
    void shouldNotReturnAuditLogsForDifferentPaymentId() {
        var paymentId1 = UUID.randomUUID();
        var paymentId2 = UUID.randomUUID();

        var entry1 = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId1)
                .fromState(null)
                .toState("INITIATED")
                .triggeredBy("INITIATE")
                .actor("system")
                .metadata(Map.of())
                .occurredAt(Instant.now())
                .build();

        var entry2 = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId2)
                .fromState(null)
                .toState("INITIATED")
                .triggeredBy("INITIATE")
                .actor("system")
                .metadata(Map.of())
                .occurredAt(Instant.now())
                .build();

        auditLogRepository.save(entry1);
        auditLogRepository.save(entry2);

        var results = auditLogRepository.findByPaymentIdOrderByOccurredAtDesc(paymentId1);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPaymentId()).isEqualTo(paymentId1);
    }

    // ── JSONB metadata round-trip ───────────────────────────────────────

    @Test
    @DisplayName("should persist and retrieve JSONB metadata in audit log")
    void shouldPersistAndRetrieveJsonbMetadataInAuditLog() {
        var paymentId = UUID.randomUUID();
        var metadata = Map.of(
                "reason", "compliance_passed",
                "riskScore", "25",
                "provider", "chainalysis"
        );

        var entry = PaymentAuditLogEntity.builder()
                .logId(UUID.randomUUID())
                .paymentId(paymentId)
                .fromState("COMPLIANCE_CHECK")
                .toState("FX_LOCKED")
                .triggeredBy("COMPLIANCE_PASSED")
                .actor("compliance-service")
                .metadata(metadata)
                .occurredAt(Instant.now())
                .build();

        auditLogRepository.save(entry);

        var results = auditLogRepository.findByPaymentIdOrderByOccurredAtDesc(paymentId);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMetadata()).containsEntry("reason", "compliance_passed");
        assertThat(results.get(0).getMetadata()).containsEntry("riskScore", "25");
        assertThat(results.get(0).getMetadata()).containsEntry("provider", "chainalysis");
    }
}
