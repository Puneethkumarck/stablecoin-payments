package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.api.request.FxQuoteRequest;
import com.stablecoin.payments.fx.api.request.FxRateLockRequest;
import com.stablecoin.payments.fx.api.response.FxQuoteResponse;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import com.stablecoin.payments.fx.application.service.FxQuoteApplicationService;
import com.stablecoin.payments.fx.application.service.FxRateLockApplicationService;
import com.stablecoin.payments.fx.application.service.FxRateLockApplicationService.LockRateResult;
import com.stablecoin.payments.fx.domain.exception.QuoteAlreadyLockedException;
import com.stablecoin.payments.fx.domain.exception.QuoteExpiredException;
import com.stablecoin.payments.fx.domain.exception.QuoteNotFoundException;
import com.stablecoin.payments.fx.domain.exception.RateUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("FxQuoteController")
class FxQuoteControllerTest {

    @Mock
    private FxQuoteApplicationService quoteApplicationService;

    @Mock
    private FxRateLockApplicationService rateLockApplicationService;

    @InjectMocks
    private FxQuoteController controller;

    @Nested
    @DisplayName("GET /v1/fx/quote")
    class GetQuote {

        @Test
        @DisplayName("should delegate to application service and return quote response")
        void shouldGetQuote() {
            // given
            var request = new FxQuoteRequest("USD", "EUR", new BigDecimal("10000.00"));
            var now = Instant.now();
            var expectedResponse = new FxQuoteResponse(
                    UUID.randomUUID(), "USD", "EUR",
                    new BigDecimal("10000.00"), new BigDecimal("9200.00"),
                    new BigDecimal("0.92"), new BigDecimal("1.087"),
                    30, new BigDecimal("30.00"), "REFINITIV",
                    now, now.plusSeconds(300));

            given(quoteApplicationService.getQuote(request)).willReturn(expectedResponse);

            // when
            var result = controller.getQuote(request);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("should propagate RateUnavailableException")
        void shouldPropagateRateUnavailable() {
            // given
            var request = new FxQuoteRequest("JPY", "BRL", new BigDecimal("10000.00"));
            given(quoteApplicationService.getQuote(request))
                    .willThrow(RateUnavailableException.forCorridor("JPY", "BRL"));

            // when/then
            assertThatThrownBy(() -> controller.getQuote(request))
                    .isInstanceOf(RateUnavailableException.class)
                    .hasMessageContaining("JPY")
                    .hasMessageContaining("BRL");
        }
    }

    @Nested
    @DisplayName("GET /v1/fx/quote/{quoteId}")
    class GetQuoteById {

        @Test
        @DisplayName("should delegate to application service and return quote response")
        void shouldGetQuoteById() {
            // given
            var quoteId = UUID.randomUUID();
            var now = Instant.now();
            var expectedResponse = new FxQuoteResponse(
                    quoteId, "USD", "EUR",
                    new BigDecimal("10000.00"), new BigDecimal("9200.00"),
                    new BigDecimal("0.92"), new BigDecimal("1.087"),
                    30, new BigDecimal("30.00"), "REFINITIV",
                    now, now.plusSeconds(300));

            given(quoteApplicationService.getQuoteById(quoteId)).willReturn(expectedResponse);

            // when
            var result = controller.getQuoteById(quoteId);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("should propagate QuoteNotFoundException")
        void shouldPropagateQuoteNotFound() {
            // given
            var quoteId = UUID.randomUUID();
            given(quoteApplicationService.getQuoteById(quoteId))
                    .willThrow(QuoteNotFoundException.withId(quoteId));

            // when/then
            assertThatThrownBy(() -> controller.getQuoteById(quoteId))
                    .isInstanceOf(QuoteNotFoundException.class)
                    .hasMessageContaining(quoteId.toString());
        }
    }

    @Nested
    @DisplayName("POST /v1/fx/lock/{quoteId}")
    class LockRate {

        @Test
        @DisplayName("should return 201 Created for new lock")
        void shouldReturn201ForNewLock() {
            // given
            var quoteId = UUID.randomUUID();
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();
            var request = new FxRateLockRequest(paymentId, correlationId, "US", "DE");
            var now = Instant.now();
            var lockResponse = new FxRateLockResponse(
                    UUID.randomUUID(), quoteId, paymentId,
                    "USD", "EUR",
                    new BigDecimal("10000.00"), new BigDecimal("9200.00"),
                    new BigDecimal("0.92"), 30, new BigDecimal("30.00"),
                    now, now.plusSeconds(30));

            given(rateLockApplicationService.lockRate(quoteId, request))
                    .willReturn(new LockRateResult(lockResponse, true));

            // when
            var result = controller.lockRate(quoteId, request);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody())
                    .usingRecursiveComparison()
                    .isEqualTo(lockResponse);
        }

        @Test
        @DisplayName("should return 200 OK for idempotent lock")
        void shouldReturn200ForIdempotentLock() {
            // given
            var quoteId = UUID.randomUUID();
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();
            var request = new FxRateLockRequest(paymentId, correlationId, "US", "DE");
            var now = Instant.now();
            var lockResponse = new FxRateLockResponse(
                    UUID.randomUUID(), quoteId, paymentId,
                    "USD", "EUR",
                    new BigDecimal("10000.00"), new BigDecimal("9200.00"),
                    new BigDecimal("0.92"), 30, new BigDecimal("30.00"),
                    now, now.plusSeconds(30));

            given(rateLockApplicationService.lockRate(quoteId, request))
                    .willReturn(new LockRateResult(lockResponse, false));

            // when
            var result = controller.lockRate(quoteId, request);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody())
                    .usingRecursiveComparison()
                    .isEqualTo(lockResponse);
        }

        @Test
        @DisplayName("should propagate QuoteNotFoundException")
        void shouldPropagateQuoteNotFound() {
            // given
            var quoteId = UUID.randomUUID();
            var request = new FxRateLockRequest(UUID.randomUUID(), UUID.randomUUID(), "US", "DE");
            given(rateLockApplicationService.lockRate(quoteId, request))
                    .willThrow(QuoteNotFoundException.withId(quoteId));

            // when/then
            assertThatThrownBy(() -> controller.lockRate(quoteId, request))
                    .isInstanceOf(QuoteNotFoundException.class)
                    .hasMessageContaining(quoteId.toString());
        }

        @Test
        @DisplayName("should propagate QuoteExpiredException")
        void shouldPropagateQuoteExpired() {
            // given
            var quoteId = UUID.randomUUID();
            var request = new FxRateLockRequest(UUID.randomUUID(), UUID.randomUUID(), "US", "DE");
            given(rateLockApplicationService.lockRate(quoteId, request))
                    .willThrow(QuoteExpiredException.withId(quoteId));

            // when/then
            assertThatThrownBy(() -> controller.lockRate(quoteId, request))
                    .isInstanceOf(QuoteExpiredException.class)
                    .hasMessageContaining(quoteId.toString());
        }

        @Test
        @DisplayName("should propagate QuoteAlreadyLockedException")
        void shouldPropagateQuoteAlreadyLocked() {
            // given
            var quoteId = UUID.randomUUID();
            var request = new FxRateLockRequest(UUID.randomUUID(), UUID.randomUUID(), "US", "DE");
            given(rateLockApplicationService.lockRate(quoteId, request))
                    .willThrow(QuoteAlreadyLockedException.withId(quoteId));

            // when/then
            assertThatThrownBy(() -> controller.lockRate(quoteId, request))
                    .isInstanceOf(QuoteAlreadyLockedException.class)
                    .hasMessageContaining(quoteId.toString());
        }
    }
}
