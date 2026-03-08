package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.event.CollectionCompletedEvent;
import com.stablecoin.payments.onramp.domain.event.CollectionFailedEvent;
import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.model.PspTransaction;
import com.stablecoin.payments.onramp.domain.model.PspTransactionDirection;
import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.port.PspTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFERENCE;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectedOrder;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectionFailedOrder;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPaymentInitiatedOrder;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.anAwaitingConfirmationOrder;
import static com.stablecoin.payments.onramp.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.onramp.fixtures.TestUtils.eqIgnoringTimestamps;
import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.aFailedCommand;
import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.aMismatchCommand;
import static com.stablecoin.payments.onramp.fixtures.WebhookFixtures.aSucceededCommand;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookCommandHandler")
class WebhookCommandHandlerTest {

    @Mock private CollectionOrderRepository orderRepository;
    @Mock private PspTransactionRepository pspTransactionRepository;
    @Mock private CollectionEventPublisher eventPublisher;

    private WebhookCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WebhookCommandHandler(orderRepository, pspTransactionRepository, eventPublisher);
    }

    @Nested
    @DisplayName("payment_intent.succeeded")
    class PaymentSucceeded {

        @Test
        @DisplayName("should transition AWAITING_CONFIRMATION to COLLECTED and publish CollectionCompletedEvent")
        void shouldTransitionToCollectedAndPublishEvent() {
            // given
            var order = anAwaitingConfirmationOrder();
            var command = aSucceededCommand();
            var collected = order.confirmCollection(command.amount());

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(collected))).willReturn(collected);

            // when
            handler.handleWebhook(command);

            // then
            then(orderRepository).should().save(eqIgnoringTimestamps(collected));
            then(eventPublisher).should().publish(eqIgnoringTimestamps(
                    new CollectionCompletedEvent(
                            collected.collectionId(),
                            collected.paymentId(),
                            collected.correlationId(),
                            collected.collectedAmount().amount(),
                            collected.collectedAmount().currency(),
                            collected.paymentRail().rail().name(),
                            collected.psp().pspName(),
                            collected.pspReference(),
                            collected.pspSettledAt())));
        }

        @Test
        @DisplayName("should detect amount mismatch and transition to AMOUNT_MISMATCH")
        void shouldDetectAmountMismatch() {
            // given
            var order = anAwaitingConfirmationOrder();
            var command = aMismatchCommand();
            var mismatch = order.detectAmountMismatch();

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(mismatch))).willReturn(mismatch);

            // when
            handler.handleWebhook(command);

            // then
            then(orderRepository).should().save(eqIgnoringTimestamps(mismatch));
            then(eventPublisher).should().publish(eqIgnoringTimestamps(
                    new CollectionFailedEvent(
                            mismatch.collectionId(),
                            mismatch.paymentId(),
                            mismatch.correlationId(),
                            "Amount mismatch: expected 1000.00 USD, received 500.00 USD",
                            "AMOUNT_MISMATCH",
                            null)));
        }
    }

    @Nested
    @DisplayName("payment_intent.payment_failed")
    class PaymentFailed {

        @Test
        @DisplayName("should transition AWAITING_CONFIRMATION to COLLECTION_FAILED and publish event")
        void shouldTransitionAwaitingToCollectionFailed() {
            // given
            var order = anAwaitingConfirmationOrder();
            var command = aFailedCommand();
            var failed = order.timeoutCollection("PSP payment failed: failed", "PSP_PAYMENT_FAILED");

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(failed))).willReturn(failed);

            // when
            handler.handleWebhook(command);

            // then
            then(orderRepository).should().save(eqIgnoringTimestamps(failed));
            then(eventPublisher).should().publish(eqIgnoringTimestamps(
                    new CollectionFailedEvent(
                            failed.collectionId(),
                            failed.paymentId(),
                            failed.correlationId(),
                            "PSP payment failed: failed",
                            "PSP_PAYMENT_FAILED",
                            null)));
        }

        @Test
        @DisplayName("should transition PAYMENT_INITIATED to COLLECTION_FAILED using failCollection()")
        void shouldTransitionPaymentInitiatedToFailed() {
            // given
            var order = aPaymentInitiatedOrder();
            var command = new WebhookCommand(
                    "evt_test_002",
                    "payment_intent.payment_failed",
                    PSP_REFERENCE,
                    null,
                    order.amount(),
                    "failed",
                    "{}");
            var failed = order.failCollection("PSP payment failed: failed", "PSP_PAYMENT_FAILED");

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(failed))).willReturn(failed);

            // when
            handler.handleWebhook(command);

            // then
            then(orderRepository).should().save(eqIgnoringTimestamps(failed));
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("should skip webhook when order is already COLLECTED for succeeded event")
        void shouldSkipAlreadyCollectedOrder() {
            // given
            var order = aCollectedOrder();
            var command = aSucceededCommand();

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));

            // when
            handler.handleWebhook(command);

            // then — no save or event publish should occur
            then(orderRepository).should(never()).save(eqIgnoringTimestamps(order));
            then(eventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should skip webhook when order is already COLLECTION_FAILED for failed event")
        void shouldSkipAlreadyFailedOrder() {
            // given
            var order = aCollectionFailedOrder();
            var command = aFailedCommand();

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));

            // when
            handler.handleWebhook(command);

            // then — no save or event publish should occur
            then(orderRepository).should(never()).save(eqIgnoringTimestamps(order));
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw CollectionOrderNotFoundException when order not found")
        void shouldThrowWhenOrderNotFound() {
            // given
            var command = aSucceededCommand();
            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> handler.handleWebhook(command))
                    .isInstanceOf(CollectionOrderNotFoundException.class)
                    .hasMessageContaining(PSP_REFERENCE);
        }

        @Test
        @DisplayName("should record PspTransaction for every non-idempotent webhook")
        void shouldRecordPspTransaction() {
            // given
            var order = anAwaitingConfirmationOrder();
            var command = aSucceededCommand();
            var collected = order.confirmCollection(command.amount());

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(collected))).willReturn(collected);

            // when
            handler.handleWebhook(command);

            // then
            then(pspTransactionRepository).should().save(
                    eqIgnoring(PspTransaction.create(
                            order.collectionId(),
                            order.psp().pspName(),
                            PSP_REFERENCE,
                            PspTransactionDirection.DEBIT,
                            "payment_intent.succeeded",
                            command.amount(),
                            "succeeded",
                            command.rawPayload()), "pspTxnId"));
        }

        @Test
        @DisplayName("should ignore unrecognised event type without saving or publishing")
        void shouldIgnoreUnrecognisedEventType() {
            // given
            var order = anAwaitingConfirmationOrder();
            var command = new WebhookCommand(
                    "evt_test_003",
                    "charge.refunded",
                    PSP_REFERENCE,
                    null,
                    order.amount(),
                    "refunded",
                    "{}");

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));

            // when
            handler.handleWebhook(command);

            // then — no state change or event publish
            then(orderRepository).should(never()).save(eqIgnoringTimestamps(order));
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }
}
