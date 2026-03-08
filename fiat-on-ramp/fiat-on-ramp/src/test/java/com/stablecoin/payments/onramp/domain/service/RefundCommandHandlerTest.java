package com.stablecoin.payments.onramp.domain.service;

import com.stablecoin.payments.onramp.domain.event.RefundCompletedEvent;
import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.exception.RefundAmountExceededException;
import com.stablecoin.payments.onramp.domain.exception.RefundNotAllowedException;
import com.stablecoin.payments.onramp.domain.exception.RefundNotFoundException;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.Refund;
import com.stablecoin.payments.onramp.domain.port.CollectionEventPublisher;
import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.port.PspGateway;
import com.stablecoin.payments.onramp.domain.port.PspRefundRequest;
import com.stablecoin.payments.onramp.domain.port.PspRefundResult;
import com.stablecoin.payments.onramp.domain.port.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFUND_REFERENCE;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectedOrder;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aPaymentInitiatedOrder;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.REFUND_REASON;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.aCompletedRefund;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.aRefundAmount;
import static com.stablecoin.payments.onramp.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.onramp.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundCommandHandler")
class RefundCommandHandlerTest {

    @Mock private CollectionOrderRepository collectionOrderRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private PspGateway pspGateway;
    @Mock private CollectionEventPublisher eventPublisher;

    private RefundCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RefundCommandHandler(collectionOrderRepository, refundRepository,
                pspGateway, eventPublisher);
    }

    @Nested
    @DisplayName("initiateRefund")
    class InitiateRefund {

        @Test
        @DisplayName("should initiate refund for collected order — saves final REFUNDED state once")
        void shouldInitiateRefundForCollectedOrder() {
            // given
            var order = aCollectedOrder();
            var collectionId = order.collectionId();
            var refundAmount = aRefundAmount();
            var reason = REFUND_REASON;

            var refundedOrder = order.initiateRefund()
                    .startRefundProcessing()
                    .completeRefund();

            var pspRefundRequest = new PspRefundRequest(
                    collectionId, order.pspReference(), refundAmount,
                    order.psp().pspName(), reason);
            var pspResult = new PspRefundResult(PSP_REFUND_REFERENCE, "succeeded");

            var expectedRefund = Refund.initiate(collectionId, order.paymentId(), refundAmount, reason)
                    .startProcessing()
                    .complete(PSP_REFUND_REFERENCE);

            var expectedEvent = new RefundCompletedEvent(
                    expectedRefund.refundId(),
                    collectionId,
                    order.paymentId(),
                    refundAmount.amount(),
                    refundAmount.currency(),
                    PSP_REFUND_REFERENCE,
                    Instant.now());

            given(collectionOrderRepository.findById(collectionId)).willReturn(Optional.of(order));
            given(refundRepository.findByCollectionId(collectionId)).willReturn(Collections.emptyList());
            given(pspGateway.initiateRefund(pspRefundRequest)).willReturn(pspResult);
            given(refundRepository.save(eqIgnoring(expectedRefund, "refundId"))).willReturn(expectedRefund);

            // when
            handler.initiateRefund(collectionId, refundAmount, reason);

            // then — single save with final REFUNDED state
            then(collectionOrderRepository).should().save(eqIgnoringTimestamps(refundedOrder));
            then(pspGateway).should().initiateRefund(pspRefundRequest);
            then(refundRepository).should().save(eqIgnoring(expectedRefund, "refundId"));
            then(eventPublisher).should().publish(eqIgnoring(expectedEvent, "refundId", "completedAt"));
        }

        @Test
        @DisplayName("should return existing refund when already initiated — idempotent")
        void shouldReturnExistingRefundWhenAlreadyInitiated() {
            // given
            var order = aCollectedOrder();
            var collectionId = order.collectionId();
            var refundAmount = aRefundAmount();
            var reason = REFUND_REASON;
            var existingRefund = aCompletedRefund();

            given(collectionOrderRepository.findById(collectionId)).willReturn(Optional.of(order));
            given(refundRepository.findByCollectionId(collectionId)).willReturn(List.of(existingRefund));

            // when
            handler.initiateRefund(collectionId, refundAmount, reason);

            // then — no save calls should occur
            then(collectionOrderRepository).should(never()).save(eqIgnoringTimestamps(order));
            then(refundRepository).should(never()).save(eqIgnoringTimestamps(existingRefund));
            then(eventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should throw CollectionOrderNotFoundException when order not found")
        void shouldThrowWhenCollectionNotFound() {
            // given
            var collectionId = UUID.randomUUID();
            var refundAmount = aRefundAmount();

            given(collectionOrderRepository.findById(collectionId)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> handler.initiateRefund(collectionId, refundAmount, REFUND_REASON))
                    .isInstanceOf(CollectionOrderNotFoundException.class)
                    .hasMessageContaining(collectionId.toString());
        }

        @Test
        @DisplayName("should throw RefundNotAllowedException when collection not in COLLECTED state")
        void shouldThrowWhenCollectionNotInCollectedState() {
            // given
            var order = aPaymentInitiatedOrder();
            var collectionId = order.collectionId();
            var refundAmount = aRefundAmount();

            given(collectionOrderRepository.findById(collectionId)).willReturn(Optional.of(order));
            given(refundRepository.findByCollectionId(collectionId)).willReturn(Collections.emptyList());

            // when/then
            assertThatThrownBy(() -> handler.initiateRefund(collectionId, refundAmount, REFUND_REASON))
                    .isInstanceOf(RefundNotAllowedException.class)
                    .hasMessageContaining(collectionId.toString());
        }

        @Test
        @DisplayName("should throw RefundAmountExceededException when refund amount exceeds collected")
        void shouldThrowWhenRefundAmountExceedsCollectedAmount() {
            // given
            var order = aCollectedOrder();
            var collectionId = order.collectionId();
            var excessiveAmount = new Money(new BigDecimal("9999.00"), "USD");

            given(collectionOrderRepository.findById(collectionId)).willReturn(Optional.of(order));
            given(refundRepository.findByCollectionId(collectionId)).willReturn(Collections.emptyList());

            // when/then
            assertThatThrownBy(() -> handler.initiateRefund(collectionId, excessiveAmount, REFUND_REASON))
                    .isInstanceOf(RefundAmountExceededException.class)
                    .hasMessageContaining(collectionId.toString());
        }
    }

    @Nested
    @DisplayName("getRefund")
    class GetRefund {

        @Test
        @DisplayName("should throw RefundNotFoundException when refund not found")
        void shouldThrowWhenRefundNotFound() {
            // given
            var refundId = UUID.randomUUID();

            given(refundRepository.findById(refundId)).willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> handler.getRefund(refundId))
                    .isInstanceOf(RefundNotFoundException.class)
                    .hasMessageContaining(refundId.toString());
        }
    }
}
