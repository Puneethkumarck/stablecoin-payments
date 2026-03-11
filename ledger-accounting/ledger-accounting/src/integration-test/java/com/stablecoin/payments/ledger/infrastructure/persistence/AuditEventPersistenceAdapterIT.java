package com.stablecoin.payments.ledger.infrastructure.persistence;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.model.AuditEvent;
import com.stablecoin.payments.ledger.domain.port.AuditEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CORRELATION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.anAuditEvent;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditEventPersistenceAdapter IT")
class AuditEventPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private AuditEventRepository adapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save event and retrieve by payment id")
    void shouldSaveAndRetrieveByPaymentId() {
        var saved = adapter.save(anAuditEvent());

        assertThat(adapter.findByPaymentId(PAYMENT_ID))
                .hasSize(1)
                .first()
                .usingRecursiveComparison()
                .ignoringFields("eventPayload")
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should retrieve by correlation id")
    void shouldRetrieveByCorrelationId() {
        var saved = adapter.save(anAuditEvent());

        assertThat(adapter.findByCorrelationId(CORRELATION_ID))
                .hasSize(1)
                .first()
                .usingRecursiveComparison()
                .ignoringFields("eventPayload")
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    // ── JSONB Round-Trip ─────────────────────────────────────────────────

    @Test
    @DisplayName("should round-trip JSONB event payload correctly")
    void shouldRoundTripJsonbEventPayload() {
        adapter.save(anAuditEvent());

        assertThat(adapter.findByPaymentId(PAYMENT_ID))
                .hasSize(1)
                .first()
                .satisfies(e -> assertThat(e.eventPayload()).contains("transactionId"));
    }

    // ── Nullable Payment ID ──────────────────────────────────────────────

    @Test
    @DisplayName("should allow null payment id")
    void shouldAllowNullPaymentId() {
        var event = AuditEvent.create(
                CORRELATION_ID,
                null,
                "ledger-accounting",
                "system.startup",
                "{\"action\": \"startup\"}",
                "system",
                Instant.now()
        );
        var saved = adapter.save(event);

        assertThat(adapter.findByCorrelationId(CORRELATION_ID))
                .hasSize(1)
                .first()
                .usingRecursiveComparison()
                .ignoringFields("eventPayload")
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty list for non-existent payment id")
    void shouldReturnEmptyForNonExistentPaymentId() {
        assertThat(adapter.findByPaymentId(UUID.randomUUID())).isEmpty();
    }
}
