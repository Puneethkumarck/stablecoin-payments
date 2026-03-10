package com.stablecoin.payments.offramp.domain.service;

import com.stablecoin.payments.offramp.domain.event.FiatPayoutFailedEvent;
import com.stablecoin.payments.offramp.domain.port.PayoutEventPublisher;
import com.stablecoin.payments.offramp.domain.port.PayoutMonitorProperties;
import com.stablecoin.payments.offramp.domain.port.PayoutOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.stablecoin.payments.offramp.domain.model.PayoutOrderTestHelper.withUpdatedAt;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PAYOUT_INITIATED;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PAYOUT_PROCESSING;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPayoutInitiatedOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPayoutProcessingOrder;
import static com.stablecoin.payments.offramp.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PayoutMonitorCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final int STUCK_THRESHOLD_MINUTES = 120;
    private static final String STUCK_REASON =
            "Payout stuck — no partner settlement received within 120 minutes";

    @Mock
    private PayoutOrderRepository orderRepository;

    @Mock
    private PayoutEventPublisher eventPublisher;

    private PayoutMonitorCommandHandler handler;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        handler = new PayoutMonitorCommandHandler(
                orderRepository,
                eventPublisher,
                new StubMonitorProperties(STUCK_THRESHOLD_MINUTES),
                passthroughTransactionManager(),
                clock
        );
    }

    @Nested
    @DisplayName("Stuck PAYOUT_INITIATED")
    class StuckPayoutInitiated {

        @Test
        void shouldEscalatePayoutInitiatedOlderThanThreshold() {
            var stuckOrder = withUpdatedAt(
                    aPayoutInitiatedOrder(), NOW.minus(Duration.ofMinutes(121)));
            var expectedEscalated = stuckOrder
                    .failPayout(STUCK_REASON)
                    .escalateToManualReview();

            given(orderRepository.findByStatus(PAYOUT_INITIATED))
                    .willReturn(List.of(stuckOrder));
            given(orderRepository.findByStatus(PAYOUT_PROCESSING))
                    .willReturn(List.of());

            handler.detectAndEscalateStuckPayouts();

            then(orderRepository).should().save(eqIgnoringTimestamps(expectedEscalated));
        }

        @Test
        void shouldPublishFiatPayoutFailedEventForStuckInitiated() {
            var stuckOrder = withUpdatedAt(
                    aPayoutInitiatedOrder(), NOW.minus(Duration.ofMinutes(121)));
            var expectedEvent = new FiatPayoutFailedEvent(
                    stuckOrder.payoutId(),
                    stuckOrder.paymentId(),
                    stuckOrder.correlationId(),
                    STUCK_REASON,
                    "FR-2003",
                    NOW
            );

            given(orderRepository.findByStatus(PAYOUT_INITIATED))
                    .willReturn(List.of(stuckOrder));
            given(orderRepository.findByStatus(PAYOUT_PROCESSING))
                    .willReturn(List.of());

            handler.detectAndEscalateStuckPayouts();

            then(eventPublisher).should().publish(eqIgnoringTimestamps(expectedEvent));
        }

        @Test
        void shouldSkipPayoutInitiatedWithinThreshold() {
            var recentOrder = withUpdatedAt(
                    aPayoutInitiatedOrder(), NOW.minus(Duration.ofMinutes(60)));

            given(orderRepository.findByStatus(PAYOUT_INITIATED))
                    .willReturn(List.of(recentOrder));
            given(orderRepository.findByStatus(PAYOUT_PROCESSING))
                    .willReturn(List.of());

            handler.detectAndEscalateStuckPayouts();

            then(orderRepository).should().findByStatus(PAYOUT_INITIATED);
            then(orderRepository).should().findByStatus(PAYOUT_PROCESSING);
            then(orderRepository).shouldHaveNoMoreInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("Stuck PAYOUT_PROCESSING")
    class StuckPayoutProcessing {

        @Test
        void shouldEscalatePayoutProcessingOlderThanThreshold() {
            var stuckOrder = withUpdatedAt(
                    aPayoutProcessingOrder(), NOW.minus(Duration.ofMinutes(150)));
            var expectedEscalated = stuckOrder
                    .failPayout(STUCK_REASON)
                    .escalateToManualReview();

            given(orderRepository.findByStatus(PAYOUT_INITIATED))
                    .willReturn(List.of());
            given(orderRepository.findByStatus(PAYOUT_PROCESSING))
                    .willReturn(List.of(stuckOrder));

            handler.detectAndEscalateStuckPayouts();

            then(orderRepository).should().save(eqIgnoringTimestamps(expectedEscalated));
        }

        @Test
        void shouldPublishFiatPayoutFailedEventForStuckProcessing() {
            var stuckOrder = withUpdatedAt(
                    aPayoutProcessingOrder(), NOW.minus(Duration.ofMinutes(150)));
            var expectedEvent = new FiatPayoutFailedEvent(
                    stuckOrder.payoutId(),
                    stuckOrder.paymentId(),
                    stuckOrder.correlationId(),
                    STUCK_REASON,
                    "FR-2003",
                    NOW
            );

            given(orderRepository.findByStatus(PAYOUT_INITIATED))
                    .willReturn(List.of());
            given(orderRepository.findByStatus(PAYOUT_PROCESSING))
                    .willReturn(List.of(stuckOrder));

            handler.detectAndEscalateStuckPayouts();

            then(eventPublisher).should().publish(eqIgnoringTimestamps(expectedEvent));
        }
    }

    @Nested
    @DisplayName("No stuck payouts")
    class NoStuckPayouts {

        @Test
        void shouldDoNothingWhenNoPayoutsFound() {
            given(orderRepository.findByStatus(PAYOUT_INITIATED))
                    .willReturn(List.of());
            given(orderRepository.findByStatus(PAYOUT_PROCESSING))
                    .willReturn(List.of());

            handler.detectAndEscalateStuckPayouts();

            then(orderRepository).should().findByStatus(PAYOUT_INITIATED);
            then(orderRepository).should().findByStatus(PAYOUT_PROCESSING);
            then(orderRepository).shouldHaveNoMoreInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    // -- Helpers ----------------------------------------------------------

    private static PlatformTransactionManager passthroughTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus(true);
            }

            @Override
            public void commit(TransactionStatus status) {
                // no-op in unit tests
            }

            @Override
            public void rollback(TransactionStatus status) {
                // no-op in unit tests
            }
        };
    }

    private record StubMonitorProperties(int stuckThresholdMinutes) implements PayoutMonitorProperties {
    }
}
