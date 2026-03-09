package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.event.CollectionInitiatedEvent;
import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.PspTransaction;
import com.stablecoin.payments.onramp.domain.model.PspTransactionDirection;
import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.port.PspGateway;
import com.stablecoin.payments.onramp.domain.port.PspPaymentRequest;
import com.stablecoin.payments.onramp.domain.port.PspPaymentResult;
import com.stablecoin.payments.onramp.domain.port.PspTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.CORRELATION_ID;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PAYMENT_ID;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFERENCE;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aBankAccount;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectedOrder;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aMoney;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPaymentRail;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPendingOrder;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPspIdentifier;
import static com.stablecoin.payments.onramp.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.onramp.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionCommandHandler")
class CollectionCommandHandlerTest {

    @Mock private CollectionOrderRepository collectionOrderRepository;
    @Mock private PspTransactionRepository pspTransactionRepository;
    @Mock private PspGateway pspGateway;
    @Mock private CollectionEventPublisher eventPublisher;

    private CollectionCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CollectionCommandHandler(collectionOrderRepository, pspTransactionRepository,
                pspGateway, eventPublisher);
    }

    @Nested
    @DisplayName("initiateCollection")
    class InitiateCollection {

        @Test
        @DisplayName("should initiate collection successfully — saves AWAITING_CONFIRMATION order")
        void shouldInitiateCollectionSuccessfully() {
            // given
            var amount = aMoney();
            var paymentRail = aPaymentRail();
            var psp = aPspIdentifier();
            var senderAccount = aBankAccount();
            var pspResult = new PspPaymentResult(PSP_REFERENCE, "requires_action");

            var expectedOrder = CollectionOrder.initiate(
                    PAYMENT_ID, CORRELATION_ID, amount, paymentRail, psp, senderAccount)
                    .initiatePayment()
                    .awaitConfirmation(PSP_REFERENCE);

            var expectedPspPaymentRequest = new PspPaymentRequest(
                    expectedOrder.collectionId(), amount, paymentRail, senderAccount, psp.pspName(),
                    expectedOrder.collectionId().toString());

            var expectedPspTransaction = PspTransaction.create(
                    expectedOrder.collectionId(),
                    psp.pspName(),
                    PSP_REFERENCE,
                    PspTransactionDirection.DEBIT,
                    "payment_intent.created",
                    amount,
                    "requires_action",
                    null);

            var expectedEvent = new CollectionInitiatedEvent(
                    expectedOrder.collectionId(),
                    PAYMENT_ID,
                    CORRELATION_ID,
                    amount.amount(),
                    amount.currency(),
                    paymentRail.rail().name(),
                    psp.pspName(),
                    Instant.now());

            given(collectionOrderRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
            given(pspGateway.initiatePayment(eqIgnoring(expectedPspPaymentRequest, "collectionId", "idempotencyKey")))
                    .willReturn(pspResult);
            given(collectionOrderRepository.save(eqIgnoring(expectedOrder, "collectionId")))
                    .willReturn(expectedOrder);

            // when
            handler.initiateCollection(PAYMENT_ID, CORRELATION_ID, amount, paymentRail, psp, senderAccount);

            // then
            then(collectionOrderRepository).should().save(eqIgnoring(expectedOrder, "collectionId"));
            then(pspTransactionRepository).should().save(eqIgnoring(expectedPspTransaction, "collectionId", "pspTxnId"));
            then(eventPublisher).should().publish(eqIgnoring(expectedEvent, "collectionId"));
        }

        @Test
        @DisplayName("should return existing order when paymentId already exists — idempotent")
        void shouldReturnExistingOrderWhenPaymentIdAlreadyExists() {
            // given
            var existingOrder = aCollectedOrder();
            var amount = aMoney();
            var paymentRail = aPaymentRail();
            var psp = aPspIdentifier();
            var senderAccount = aBankAccount();

            given(collectionOrderRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(existingOrder));

            // when
            handler.initiateCollection(PAYMENT_ID, CORRELATION_ID, amount, paymentRail, psp, senderAccount);

            // then — no save or PSP calls should occur
            then(collectionOrderRepository).should(never()).save(eqIgnoringTimestamps(existingOrder));
            then(pspGateway).shouldHaveNoInteractions();
            then(pspTransactionRepository).shouldHaveNoInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("getCollection")
    class GetCollection {

        @Test
        @DisplayName("should get collection by id")
        void shouldGetCollectionById() {
            // given
            var order = aPendingOrder();
            var collectionId = order.collectionId();

            given(collectionOrderRepository.findById(collectionId)).willReturn(Optional.of(order));

            // when
            handler.getCollection(collectionId);

            // then
            then(collectionOrderRepository).should().findById(collectionId);
        }

        @Test
        @DisplayName("should throw when collection not found")
        void shouldThrowWhenCollectionNotFound() {
            // given
            var collectionId = UUID.randomUUID();

            given(collectionOrderRepository.findById(collectionId)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> handler.getCollection(collectionId))
                    .isInstanceOf(CollectionOrderNotFoundException.class)
                    .hasMessageContaining(collectionId.toString());
        }
    }

    @Nested
    @DisplayName("getCollectionByPaymentId")
    class GetCollectionByPaymentId {

        @Test
        @DisplayName("should get collection by payment id")
        void shouldGetCollectionByPaymentId() {
            // given
            var order = aPendingOrder();

            given(collectionOrderRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(order));

            // when
            handler.getCollectionByPaymentId(PAYMENT_ID);

            // then
            then(collectionOrderRepository).should().findByPaymentId(PAYMENT_ID);
        }

        @Test
        @DisplayName("should throw when collection not found by payment id")
        void shouldThrowWhenCollectionNotFoundByPaymentId() {
            // given
            var paymentId = UUID.randomUUID();

            given(collectionOrderRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> handler.getCollectionByPaymentId(paymentId))
                    .isInstanceOf(CollectionOrderNotFoundException.class)
                    .hasMessageContaining(paymentId.toString());
        }
    }
}
