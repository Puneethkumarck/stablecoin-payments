package com.stablecoin.payments.ledger.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CORRELATION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.anAuditEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuditEvent")
class AuditEventTest {

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("creates audit event with all fields")
        void createsAuditEvent() {
            var event = anAuditEvent();

            assertThat(event.serviceName()).isEqualTo("ledger-accounting");
            assertThat(event.eventType()).isEqualTo("journal.posted");
        }

        @Test
        @DisplayName("paymentId is nullable")
        void paymentIdIsNullable() {
            var event = new AuditEvent(
                    UUID.randomUUID(), CORRELATION_ID, null,
                    "ledger-accounting", "system.startup",
                    "{}", "system", NOW, NOW
            );

            assertThat(event.paymentId()).isNull();
        }

        @Test
        @DisplayName("actor is nullable")
        void actorIsNullable() {
            var event = new AuditEvent(
                    UUID.randomUUID(), CORRELATION_ID, PAYMENT_ID,
                    "ledger-accounting", "journal.posted",
                    "{}", null, NOW, NOW
            );

            assertThat(event.actor()).isNull();
        }
    }

    @Nested
    @DisplayName("create() factory method")
    class FactoryMethod {

        @Test
        @DisplayName("generates random auditId")
        void generatesRandomAuditId() {
            var event1 = AuditEvent.create(CORRELATION_ID, PAYMENT_ID, "svc", "type", "{}", "actor", NOW);
            var event2 = AuditEvent.create(CORRELATION_ID, PAYMENT_ID, "svc", "type", "{}", "actor", NOW);

            assertThat(event1.auditId()).isNotEqualTo(event2.auditId());
        }

        @Test
        @DisplayName("sets receivedAt to current time")
        void setsReceivedAt() {
            var before = Instant.now();
            var event = AuditEvent.create(CORRELATION_ID, PAYMENT_ID, "svc", "type", "{}", "actor", NOW);

            assertThat(event.receivedAt()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects null auditId")
        void rejectsNullAuditId() {
            assertThatThrownBy(() -> new AuditEvent(
                    null, CORRELATION_ID, PAYMENT_ID,
                    "svc", "type", "{}", "actor", NOW, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("auditId");
        }

        @Test
        @DisplayName("rejects null correlationId")
        void rejectsNullCorrelationId() {
            assertThatThrownBy(() -> new AuditEvent(
                    UUID.randomUUID(), null, PAYMENT_ID,
                    "svc", "type", "{}", "actor", NOW, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("correlationId");
        }

        @Test
        @DisplayName("rejects null serviceName")
        void rejectsNullServiceName() {
            assertThatThrownBy(() -> new AuditEvent(
                    UUID.randomUUID(), CORRELATION_ID, PAYMENT_ID,
                    null, "type", "{}", "actor", NOW, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("serviceName");
        }

        @Test
        @DisplayName("rejects null eventType")
        void rejectsNullEventType() {
            assertThatThrownBy(() -> new AuditEvent(
                    UUID.randomUUID(), CORRELATION_ID, PAYMENT_ID,
                    "svc", null, "{}", "actor", NOW, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("eventType");
        }

        @Test
        @DisplayName("rejects null eventPayload")
        void rejectsNullEventPayload() {
            assertThatThrownBy(() -> new AuditEvent(
                    UUID.randomUUID(), CORRELATION_ID, PAYMENT_ID,
                    "svc", "type", null, "actor", NOW, NOW
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("eventPayload");
        }
    }
}
