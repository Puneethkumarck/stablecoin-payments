package com.stablecoin.payments.custody.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransferLifecycleEvent")
class TransferLifecycleEventTest {

    private static final UUID TRANSFER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Nested
    @DisplayName("Simple Factory — record(transferId, state)")
    class SimpleFactory {

        @Test
        @DisplayName("creates event with correct fields")
        void createsEvent() {
            var result = TransferLifecycleEvent.record(TRANSFER_ID, "PENDING");

            var expected = TransferLifecycleEvent.record(TRANSFER_ID, "PENDING");
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("eventId", "occurredAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("rejects null transferId")
        void rejectsNullTransferId() {
            assertThatThrownBy(() -> TransferLifecycleEvent.record(null, "PENDING"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("transferId is required");
        }

        @Test
        @DisplayName("rejects null state")
        void rejectsNullState() {
            assertThatThrownBy(() -> TransferLifecycleEvent.record(TRANSFER_ID, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("state is required");
        }

        @Test
        @DisplayName("rejects blank state")
        void rejectsBlankState() {
            assertThatThrownBy(() -> TransferLifecycleEvent.record(TRANSFER_ID, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("state is required");
        }

        @Test
        @DisplayName("rejects empty state")
        void rejectsEmptyState() {
            assertThatThrownBy(() -> TransferLifecycleEvent.record(TRANSFER_ID, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("state is required");
        }
    }

    @Nested
    @DisplayName("Participant Factory — record(transferId, state, participantType, address)")
    class ParticipantFactory {

        @Test
        @DisplayName("creates event with participant details")
        void createsEventWithParticipant() {
            var result = TransferLifecycleEvent.record(
                    TRANSFER_ID, "SUBMITTED", "INPUT", "0xSenderAddress"
            );

            var expected = TransferLifecycleEvent.record(
                    TRANSFER_ID, "SUBMITTED", "INPUT", "0xSenderAddress"
            );
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("eventId", "occurredAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("allows null participantType")
        void allowsNullParticipantType() {
            var result = TransferLifecycleEvent.record(
                    TRANSFER_ID, "CONFIRMED", null, "0xAddress"
            );

            assertThat(result.participantType()).isNull();
        }

        @Test
        @DisplayName("allows null address")
        void allowsNullAddress() {
            var result = TransferLifecycleEvent.record(
                    TRANSFER_ID, "CONFIRMED", "OUTPUT", null
            );

            assertThat(result.address()).isNull();
        }

        @Test
        @DisplayName("rejects null transferId")
        void rejectsNullTransferId() {
            assertThatThrownBy(() -> TransferLifecycleEvent.record(
                    null, "SUBMITTED", "INPUT", "0x"
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("transferId is required");
        }

        @Test
        @DisplayName("rejects null state")
        void rejectsNullState() {
            assertThatThrownBy(() -> TransferLifecycleEvent.record(
                    TRANSFER_ID, null, "INPUT", "0x"
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("state is required");
        }

        @Test
        @DisplayName("rejects blank state")
        void rejectsBlankState() {
            assertThatThrownBy(() -> TransferLifecycleEvent.record(
                    TRANSFER_ID, "  ", "INPUT", "0x"
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("state is required");
        }
    }

    @Nested
    @DisplayName("Unique Event IDs")
    class UniqueEventIds {

        @Test
        @DisplayName("generates unique eventId for each call")
        void generatesUniqueIds() {
            var event1 = TransferLifecycleEvent.record(TRANSFER_ID, "PENDING");
            var event2 = TransferLifecycleEvent.record(TRANSFER_ID, "PENDING");

            assertThat(event1.eventId()).isNotEqualTo(event2.eventId());
        }
    }
}
