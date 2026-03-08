package com.stablecoin.payments.onramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.onramp.domain.model.RefundStatus.COMPLETED;
import static com.stablecoin.payments.onramp.domain.model.RefundStatus.FAILED;
import static com.stablecoin.payments.onramp.domain.model.RefundStatus.PENDING;
import static com.stablecoin.payments.onramp.domain.model.RefundStatus.PROCESSING;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PAYMENT_ID;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFUND_REFERENCE;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.COLLECTION_ID;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.FAILURE_REASON;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.REFUND_REASON;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.aCompletedRefund;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.aFailedRefund;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.aPendingRefund;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.aProcessingRefund;
import static com.stablecoin.payments.onramp.fixtures.RefundFixtures.aRefundAmount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Refund")
class RefundTest {

    @Nested
    @DisplayName("Factory Method")
    class FactoryMethod {

        @Test
        @DisplayName("creates refund in PENDING state with all fields populated")
        void createsRefundInPendingState() {
            var refund = Refund.initiate(COLLECTION_ID, PAYMENT_ID, aRefundAmount(), REFUND_REASON);

            var expected = Refund.initiate(COLLECTION_ID, PAYMENT_ID, aRefundAmount(), REFUND_REASON);

            assertThat(refund)
                    .usingRecursiveComparison()
                    .ignoringFields("refundId", "initiatedAt")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("generates unique refundId")
        void generatesUniqueRefundId() {
            var refund = aPendingRefund();

            assertThat(refund.refundId()).isNotNull();
        }

        @Test
        @DisplayName("sets status to PENDING")
        void setsStatusToPending() {
            var refund = aPendingRefund();

            assertThat(refund.status()).isEqualTo(PENDING);
        }

        @Test
        @DisplayName("sets initiatedAt timestamp")
        void setsInitiatedAt() {
            var refund = aPendingRefund();

            assertThat(refund.initiatedAt()).isNotNull();
        }

        @Test
        @DisplayName("rejects null collectionId")
        void rejectsNullCollectionId() {
            assertThatThrownBy(() -> Refund.initiate(null, PAYMENT_ID, aRefundAmount(), REFUND_REASON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("collectionId is required");
        }

        @Test
        @DisplayName("rejects null paymentId")
        void rejectsNullPaymentId() {
            assertThatThrownBy(() -> Refund.initiate(COLLECTION_ID, null, aRefundAmount(), REFUND_REASON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("paymentId is required");
        }

        @Test
        @DisplayName("rejects null refundAmount")
        void rejectsNullRefundAmount() {
            assertThatThrownBy(() -> Refund.initiate(COLLECTION_ID, PAYMENT_ID, null, REFUND_REASON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("refundAmount is required");
        }

        @Test
        @DisplayName("rejects null reason")
        void rejectsNullReason() {
            assertThatThrownBy(() -> Refund.initiate(COLLECTION_ID, PAYMENT_ID, aRefundAmount(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("reason is required");
        }

        @Test
        @DisplayName("rejects blank reason")
        void rejectsBlankReason() {
            assertThatThrownBy(() -> Refund.initiate(COLLECTION_ID, PAYMENT_ID, aRefundAmount(), "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("reason is required");
        }
    }

    @Nested
    @DisplayName("Happy Path — PENDING -> PROCESSING -> COMPLETED")
    class HappyPath {

        @Test
        @DisplayName("startProcessing() transitions PENDING -> PROCESSING")
        void pendingToProcessing() {
            var refund = aPendingRefund();

            var result = refund.startProcessing();

            assertThat(result.status()).isEqualTo(PROCESSING);
        }

        @Test
        @DisplayName("startProcessing() preserves refund data")
        void startProcessingPreservesData() {
            var refund = aPendingRefund();

            var result = refund.startProcessing();

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("status")
                    .isEqualTo(refund);
        }

        @Test
        @DisplayName("complete() transitions PROCESSING -> COMPLETED")
        void processingToCompleted() {
            var refund = aProcessingRefund();

            var result = refund.complete(PSP_REFUND_REFERENCE);

            assertThat(result.status()).isEqualTo(COMPLETED);
        }

        @Test
        @DisplayName("complete() stores PSP refund reference and completedAt")
        void completeStoresData() {
            var refund = aProcessingRefund();

            var result = refund.complete(PSP_REFUND_REFERENCE);

            assertThat(result.pspRefundRef()).isEqualTo(PSP_REFUND_REFERENCE);
            assertThat(result.completedAt()).isNotNull();
        }

        @Test
        @DisplayName("full lifecycle PENDING -> PROCESSING -> COMPLETED")
        void fullLifecycle() {
            var pending = aPendingRefund();

            var completed = pending.startProcessing()
                    .complete(PSP_REFUND_REFERENCE);

            assertThat(completed.status()).isEqualTo(COMPLETED);
            assertThat(completed.pspRefundRef()).isEqualTo(PSP_REFUND_REFERENCE);
            assertThat(completed.completedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Failure Path — PENDING -> PROCESSING -> FAILED")
    class FailurePath {

        @Test
        @DisplayName("fail() transitions PROCESSING -> FAILED")
        void processingToFailed() {
            var refund = aProcessingRefund();

            var result = refund.fail(FAILURE_REASON);

            assertThat(result.status()).isEqualTo(FAILED);
        }

        @Test
        @DisplayName("fail() stores failure reason")
        void failStoresFailureReason() {
            var refund = aProcessingRefund();

            var result = refund.fail(FAILURE_REASON);

            assertThat(result.failureReason()).isEqualTo(FAILURE_REASON);
        }

        @Test
        @DisplayName("full failure lifecycle PENDING -> PROCESSING -> FAILED")
        void fullFailureLifecycle() {
            var pending = aPendingRefund();

            var failed = pending.startProcessing()
                    .fail(FAILURE_REASON);

            assertThat(failed.status()).isEqualTo(FAILED);
            assertThat(failed.failureReason()).isEqualTo(FAILURE_REASON);
        }
    }

    @Nested
    @DisplayName("Invalid Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("complete() from PENDING throws IllegalStateException")
        void completeFromPendingThrows() {
            var refund = aPendingRefund();

            assertThatThrownBy(() -> refund.complete(PSP_REFUND_REFERENCE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot complete from state PENDING");
        }

        @Test
        @DisplayName("fail() from PENDING throws IllegalStateException")
        void failFromPendingThrows() {
            var refund = aPendingRefund();

            assertThatThrownBy(() -> refund.fail(FAILURE_REASON))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot fail from state PENDING");
        }

        @Test
        @DisplayName("startProcessing() from COMPLETED throws IllegalStateException")
        void startProcessingFromCompletedThrows() {
            var refund = aCompletedRefund();

            assertThatThrownBy(refund::startProcessing)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot start processing from state COMPLETED");
        }

        @Test
        @DisplayName("startProcessing() from FAILED throws IllegalStateException")
        void startProcessingFromFailedThrows() {
            var refund = aFailedRefund();

            assertThatThrownBy(refund::startProcessing)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot start processing from state FAILED");
        }

        @Test
        @DisplayName("complete() from COMPLETED throws IllegalStateException")
        void completeFromCompletedThrows() {
            var refund = aCompletedRefund();

            assertThatThrownBy(() -> refund.complete(PSP_REFUND_REFERENCE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot complete from state COMPLETED");
        }

        @Test
        @DisplayName("fail() from COMPLETED throws IllegalStateException")
        void failFromCompletedThrows() {
            var refund = aCompletedRefund();

            assertThatThrownBy(() -> refund.fail(FAILURE_REASON))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot fail from state COMPLETED");
        }

        @Test
        @DisplayName("complete() from FAILED throws IllegalStateException")
        void completeFromFailedThrows() {
            var refund = aFailedRefund();

            assertThatThrownBy(() -> refund.complete(PSP_REFUND_REFERENCE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot complete from state FAILED");
        }

        @Test
        @DisplayName("fail() from FAILED throws IllegalStateException")
        void failFromFailedThrows() {
            var refund = aFailedRefund();

            assertThatThrownBy(() -> refund.fail(FAILURE_REASON))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot fail from state FAILED");
        }

        @Test
        @DisplayName("startProcessing() from PROCESSING throws IllegalStateException")
        void startProcessingFromProcessingThrows() {
            var refund = aProcessingRefund();

            assertThatThrownBy(refund::startProcessing)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot start processing from state PROCESSING");
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("complete() rejects null PSP refund reference")
        void completeRejectsNullPspRefundRef() {
            var refund = aProcessingRefund();

            assertThatThrownBy(() -> refund.complete(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PSP refund reference is required");
        }

        @Test
        @DisplayName("complete() rejects blank PSP refund reference")
        void completeRejectsBlankPspRefundRef() {
            var refund = aProcessingRefund();

            assertThatThrownBy(() -> refund.complete("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PSP refund reference is required");
        }

        @Test
        @DisplayName("fail() rejects null failure reason")
        void failRejectsNullFailureReason() {
            var refund = aProcessingRefund();

            assertThatThrownBy(() -> refund.fail(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Failure reason is required");
        }

        @Test
        @DisplayName("fail() rejects blank failure reason")
        void failRejectsBlankFailureReason() {
            var refund = aProcessingRefund();

            assertThatThrownBy(() -> refund.fail("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Failure reason is required");
        }
    }
}
