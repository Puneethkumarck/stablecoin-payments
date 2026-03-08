package com.stablecoin.payments.orchestrator.application.controller;

import com.stablecoin.payments.orchestrator.domain.model.PaymentNotCancellableException;
import com.stablecoin.payments.orchestrator.domain.model.PaymentNotFoundException;
import com.stablecoin.payments.orchestrator.domain.model.PaymentState;
import com.stablecoin.payments.orchestrator.domain.service.PaymentCommandHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.IDEMPOTENCY_KEY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.RECIPIENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SENDER_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_AMOUNT_VALUE;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_COUNTRY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SOURCE_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.TARGET_COUNTRY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.TARGET_CURRENCY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anIdempotentReplayResult;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anInitiateResult;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.anInitiatedPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController")
class PaymentControllerTest {

    @Mock
    private PaymentCommandHandler commandHandler;

    @InjectMocks
    private PaymentController controller;

    @Nested
    @DisplayName("POST /v1/payments")
    class InitiatePayment {

        @Test
        @DisplayName("should return 201 Created for new payment")
        void shouldReturn201ForNewPayment() {
            // given
            var request = new InitiatePaymentRequest(
                    SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT_VALUE,
                    SOURCE_CURRENCY, TARGET_CURRENCY,
                    SOURCE_COUNTRY, TARGET_COUNTRY
            );
            var initiateResult = anInitiateResult();

            given(commandHandler.initiatePayment(
                    eq(IDEMPOTENCY_KEY), any(UUID.class),
                    eq(SENDER_ID), eq(RECIPIENT_ID), eq(SOURCE_AMOUNT_VALUE),
                    eq(SOURCE_CURRENCY), eq(TARGET_CURRENCY),
                    eq(SOURCE_COUNTRY), eq(TARGET_COUNTRY)))
                    .willReturn(initiateResult);

            // when
            var response = controller.initiatePayment(IDEMPOTENCY_KEY, request);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            var expected = PaymentResponse.from(initiateResult.payment());
            assertThat(response.getBody())
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should return 200 OK for idempotent replay")
        void shouldReturn200ForReplay() {
            // given
            var request = new InitiatePaymentRequest(
                    SENDER_ID, RECIPIENT_ID, SOURCE_AMOUNT_VALUE,
                    SOURCE_CURRENCY, TARGET_CURRENCY,
                    SOURCE_COUNTRY, TARGET_COUNTRY
            );
            var replayResult = anIdempotentReplayResult();

            given(commandHandler.initiatePayment(
                    eq(IDEMPOTENCY_KEY), any(UUID.class),
                    eq(SENDER_ID), eq(RECIPIENT_ID), eq(SOURCE_AMOUNT_VALUE),
                    eq(SOURCE_CURRENCY), eq(TARGET_CURRENCY),
                    eq(SOURCE_COUNTRY), eq(TARGET_COUNTRY)))
                    .willReturn(replayResult);

            // when
            var response = controller.initiatePayment(IDEMPOTENCY_KEY, request);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            var expected = PaymentResponse.from(replayResult.payment());
            assertThat(response.getBody())
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("GET /v1/payments/{paymentId}")
    class GetPayment {

        @Test
        @DisplayName("should return payment response when found")
        void shouldReturnPayment() {
            // given
            var payment = anInitiatedPayment();
            given(commandHandler.getPayment(payment.paymentId()))
                    .willReturn(payment);

            // when
            var result = controller.getPayment(payment.paymentId());

            // then
            var expected = PaymentResponse.from(payment);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should propagate PaymentNotFoundException")
        void shouldPropagateNotFound() {
            // given
            var paymentId = UUID.randomUUID();
            given(commandHandler.getPayment(paymentId))
                    .willThrow(new PaymentNotFoundException(paymentId));

            // when/then
            assertThatThrownBy(() -> controller.getPayment(paymentId))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining(paymentId.toString());
        }
    }

    @Nested
    @DisplayName("POST /v1/payments/{paymentId}/cancel")
    class CancelPayment {

        @Test
        @DisplayName("should return payment response when cancel accepted")
        void shouldReturnPaymentOnCancel() {
            // given
            var payment = anInitiatedPayment();
            var cancelRequest = new CancelPaymentRequest("Customer requested");

            given(commandHandler.cancelPayment(payment.paymentId(), "Customer requested"))
                    .willReturn(payment);

            // when
            var result = controller.cancelPayment(payment.paymentId(), cancelRequest);

            // then
            var expected = PaymentResponse.from(payment);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should propagate PaymentNotCancellableException for terminal payment")
        void shouldPropagateNotCancellable() {
            // given
            var paymentId = UUID.randomUUID();
            var cancelRequest = new CancelPaymentRequest("reason");

            given(commandHandler.cancelPayment(paymentId, "reason"))
                    .willThrow(new PaymentNotCancellableException(paymentId, PaymentState.COMPLETED));

            // when/then
            assertThatThrownBy(() -> controller.cancelPayment(paymentId, cancelRequest))
                    .isInstanceOf(PaymentNotCancellableException.class)
                    .hasMessageContaining(paymentId.toString());
        }
    }
}
