package com.stablecoin.payments.orchestrator.infrastructure.activity;

import com.stablecoin.payments.orchestrator.domain.event.PaymentCompensationStarted;
import com.stablecoin.payments.orchestrator.domain.event.PaymentFailed;
import com.stablecoin.payments.orchestrator.domain.model.PaymentState;
import com.stablecoin.payments.orchestrator.domain.port.PaymentEventPublisher;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.PaymentEventRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.orchestrator.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublishingActivityImpl")
class EventPublishingActivityImplTest {

    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private EventPublishingActivityImpl activity;

    private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Test
    @DisplayName("should publish PaymentFailed event via outbox")
    void shouldPublishPaymentFailedEvent() {
        // given
        var request = PaymentEventRequest.failed(
                PAYMENT_ID, CORRELATION_ID, "COMPLIANCE_CHECK",
                "Sanctions hit", "COMPLIANCE_REJECTED");

        // when
        activity.publishPaymentEvent(request);

        // then
        var expected = new PaymentFailed(
                PAYMENT_ID, CORRELATION_ID,
                PaymentState.COMPLIANCE_CHECK,
                "Sanctions hit", "COMPLIANCE_REJECTED",
                Instant.now());
        then(eventPublisher).should().publish(eqIgnoringTimestamps(expected));
    }

    @Test
    @DisplayName("should publish PaymentCompensationStarted event via outbox")
    void shouldPublishCompensationStartedEvent() {
        // given
        var request = PaymentEventRequest.cancelled(
                PAYMENT_ID, CORRELATION_ID, "Customer requested cancellation");

        // when
        activity.publishPaymentEvent(request);

        // then
        var expected = new PaymentCompensationStarted(
                PAYMENT_ID, CORRELATION_ID,
                "Customer requested cancellation",
                Instant.now());
        then(eventPublisher).should().publish(eqIgnoringTimestamps(expected));
    }

    @Test
    @DisplayName("should throw on unknown event type")
    void shouldThrowOnUnknownEventType() {
        // given
        var request = new PaymentEventRequest(
                "payment.unknown", PAYMENT_ID, CORRELATION_ID,
                null, null, null);

        // when/then
        assertThatThrownBy(() -> activity.publishPaymentEvent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payment.unknown");
    }
}
