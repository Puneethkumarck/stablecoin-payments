package com.stablecoin.payments.ledger.domain.event;

import com.stablecoin.payments.ledger.api.events.ReconciliationCompletedEvent;
import com.stablecoin.payments.ledger.api.events.ReconciliationDiscrepancyEvent;
import com.stablecoin.payments.ledger.domain.model.ReconciliationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CORRELATION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.TRANSACTION_ID;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.REC_ID;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain Events")
class DomainEventTest {

    @Nested
    @DisplayName("JournalPostedEvent")
    class JournalPosted {

        @Test
        @DisplayName("carries transaction and payment context")
        void carriesContext() {
            var event = new JournalPostedEvent(
                    TRANSACTION_ID, PAYMENT_ID, CORRELATION_ID,
                    "payment.initiated", 2, NOW
            );

            assertThat(event.transactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(event.paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(event.entryCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("ReconciliationCompletedDomainEvent")
    class ReconciliationCompleted {

        @Test
        @DisplayName("carries rec_id and payment context")
        void carriesContext() {
            var event = new ReconciliationCompletedDomainEvent(
                    REC_ID, PAYMENT_ID, ReconciliationStatus.RECONCILED, NOW
            );

            assertThat(event.recId()).isEqualTo(REC_ID);
            assertThat(event.status()).isEqualTo(ReconciliationStatus.RECONCILED);
        }
    }

    @Nested
    @DisplayName("ReconciliationDiscrepancyDomainEvent")
    class ReconciliationDiscrepancy {

        @Test
        @DisplayName("carries discrepancy details")
        void carriesDiscrepancyDetails() {
            var event = new ReconciliationDiscrepancyDomainEvent(
                    REC_ID, PAYMENT_ID, new BigDecimal("0.05"), "USD",
                    "FIAT_IN amount mismatch", NOW
            );

            assertThat(event.discrepancy()).isEqualByComparingTo(new BigDecimal("0.05"));
            assertThat(event.currency()).isEqualTo("USD");
            assertThat(event.detail()).isEqualTo("FIAT_IN amount mismatch");
        }
    }

    @Nested
    @DisplayName("API Events — topic constants")
    class ApiEvents {

        @Test
        @DisplayName("ReconciliationCompletedEvent has correct TOPIC")
        void completedEventTopic() {
            assertThat(ReconciliationCompletedEvent.TOPIC).isEqualTo("reconciliation.completed");
        }

        @Test
        @DisplayName("ReconciliationCompletedEvent has correct EVENT_TYPE")
        void completedEventType() {
            assertThat(ReconciliationCompletedEvent.EVENT_TYPE).isEqualTo("reconciliation.completed");
        }

        @Test
        @DisplayName("ReconciliationCompletedEvent schema version is 1")
        void completedSchemaVersion() {
            assertThat(ReconciliationCompletedEvent.SCHEMA_VERSION).isEqualTo(1);
        }

        @Test
        @DisplayName("ReconciliationDiscrepancyEvent has correct TOPIC")
        void discrepancyEventTopic() {
            assertThat(ReconciliationDiscrepancyEvent.TOPIC).isEqualTo("reconciliation.discrepancy");
        }

        @Test
        @DisplayName("ReconciliationDiscrepancyEvent has correct EVENT_TYPE")
        void discrepancyEventType() {
            assertThat(ReconciliationDiscrepancyEvent.EVENT_TYPE).isEqualTo("reconciliation.discrepancy");
        }

        @Test
        @DisplayName("ReconciliationDiscrepancyEvent schema version is 1")
        void discrepancySchemaVersion() {
            assertThat(ReconciliationDiscrepancyEvent.SCHEMA_VERSION).isEqualTo(1);
        }
    }
}
