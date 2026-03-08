package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.event.CollectionCompletedEvent;
import com.stablecoin.payments.onramp.domain.event.CollectionFailedEvent;
import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.model.CollectionStatus;
import com.stablecoin.payments.onramp.domain.model.PspTransaction;
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
import static org.assertj.core.api.Assertions.assertThat;
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
        void transitionsToCollectedAndPublishesEvent() {
            var order = anAwaitingConfirmationOrder();
            var command = aSucceededCommand();
            var collected = order.confirmCollection(command.amount());

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(collected))).willReturn(collected);

            handler.handleWebhook(command);

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
        void detectsAmountMismatch() {
            var order = anAwaitingConfirmationOrder();
            var command = aMismatchCommand();
            var mismatch = order.detectAmountMismatch();

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(mismatch))).willReturn(mismatch);

            handler.handleWebhook(command);

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
        void transitionsAwaitingToCollectionFailed() {
            var order = anAwaitingConfirmationOrder();
            var command = aFailedCommand();
            var failed = order.timeoutCollection("PSP payment failed: failed", "PSP_PAYMENT_FAILED");

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(failed))).willReturn(failed);

            handler.handleWebhook(command);

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
        void transitionsPaymentInitiatedToFailed() {
            var order = aPaymentInitiatedOrder();
            var command = new WebhookCommand(
                    "evt_test_002",
                    "payment_intent.payment_failed",
                    PSP_REFERENCE,
                    null,
                    order.amount(),
                    "failed",
                    "{}");

            // Need an order with pspReference set so findByPspReference works.
            // PaymentInitiated doesn't have pspReference set, but the handler looks it up.
            // Let's set it via a builder proxy (the order in PAYMENT_INITIATED wouldn't normally
            // have a pspReference, but for test purposes the repo returns it).
            var failed = order.failCollection("PSP payment failed: failed", "PSP_PAYMENT_FAILED");

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(failed))).willReturn(failed);

            handler.handleWebhook(command);

            then(orderRepository).should().save(eqIgnoringTimestamps(failed));
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("should skip webhook when order is already COLLECTED for succeeded event")
        void skipsAlreadyCollectedOrder() {
            var order = aCollectedOrder();
            var command = aSucceededCommand();

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));

            var result = handler.handleWebhook(command);

            assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
            then(orderRepository).should(never()).save(eqIgnoringTimestamps(order));
            then(eventPublisher).should(never()).publish(eqIgnoringTimestamps(
                    new CollectionCompletedEvent(null, null, null, null, null, null, null, null, null)));
        }

        @Test
        @DisplayName("should skip webhook when order is already COLLECTION_FAILED for failed event")
        void skipsAlreadyFailedOrder() {
            var order = aCollectionFailedOrder();
            var command = aFailedCommand();

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));

            var result = handler.handleWebhook(command);

            assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTION_FAILED);
            then(orderRepository).should(never()).save(eqIgnoringTimestamps(order));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw CollectionOrderNotFoundException when order not found")
        void throwsWhenOrderNotFound() {
            var command = aSucceededCommand();

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handleWebhook(command))
                    .isInstanceOf(CollectionOrderNotFoundException.class)
                    .hasMessageContaining(PSP_REFERENCE);
        }

        @Test
        @DisplayName("should record PspTransaction for every non-idempotent webhook")
        void recordsPspTransaction() {
            var order = anAwaitingConfirmationOrder();
            var command = aSucceededCommand();
            var collected = order.confirmCollection(command.amount());

            given(orderRepository.findByPspReference(PSP_REFERENCE)).willReturn(Optional.of(order));
            given(orderRepository.save(eqIgnoringTimestamps(collected))).willReturn(collected);

            handler.handleWebhook(command);

            then(pspTransactionRepository).should().save(
                    eqIgnoring(PspTransaction.create(
                            order.collectionId(),
                            order.psp().pspName(),
                            PSP_REFERENCE,
                            com.stablecoin.payments.onramp.domain.model.PspTransactionDirection.DEBIT,
                            "payment_intent.succeeded",
                            command.amount(),
                            "succeeded",
                            command.rawPayload()), "pspTxnId"));
        }

        @Test
        @DisplayName("should ignore unrecognised event type and return order unchanged")
        void ignoresUnrecognisedEventType() {
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

            var result = handler.handleWebhook(command);

            assertThat(result.status()).isEqualTo(CollectionStatus.AWAITING_CONFIRMATION);
            then(orderRepository).should(never()).save(eqIgnoringTimestamps(order));
        }
    }
}
