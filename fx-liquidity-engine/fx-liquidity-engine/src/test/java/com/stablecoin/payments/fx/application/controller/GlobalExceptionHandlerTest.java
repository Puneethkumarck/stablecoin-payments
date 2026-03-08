package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.api.response.ApiError;
import com.stablecoin.payments.fx.domain.exception.CorridorNotSupportedException;
import com.stablecoin.payments.fx.domain.exception.InsufficientLiquidityException;
import com.stablecoin.payments.fx.domain.exception.PoolNotFoundException;
import com.stablecoin.payments.fx.domain.exception.QuoteAlreadyLockedException;
import com.stablecoin.payments.fx.domain.exception.QuoteExpiredException;
import com.stablecoin.payments.fx.domain.exception.QuoteNotFoundException;
import com.stablecoin.payments.fx.domain.exception.RateUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.fx.application.controller.ErrorCodes.CORRIDOR_NOT_SUPPORTED;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.INSUFFICIENT_LIQUIDITY;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.INTERNAL_ERROR;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.LOCK_NOT_FOUND;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.QUOTE_ALREADY_LOCKED;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.QUOTE_EXPIRED;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.QUOTE_NOT_FOUND;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.RATE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHandleQuoteNotFoundException() {
        // given
        var quoteId = UUID.randomUUID();
        var ex = QuoteNotFoundException.withId(quoteId);

        // when
        var result = handler.handleQuoteNotFound(ex);

        // then
        var expected = ApiError.of(QUOTE_NOT_FOUND, "Not Found", ex.getMessage());
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    void shouldHandleQuoteExpiredException() {
        // given
        var quoteId = UUID.randomUUID();
        var ex = QuoteExpiredException.withId(quoteId);

        // when
        var result = handler.handleQuoteExpired(ex);

        // then
        var expected = ApiError.of(QUOTE_EXPIRED, "Gone", ex.getMessage());
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    void shouldHandleQuoteAlreadyLockedException() {
        // given
        var quoteId = UUID.randomUUID();
        var ex = QuoteAlreadyLockedException.withId(quoteId);

        // when
        var result = handler.handleQuoteAlreadyLocked(ex);

        // then
        var expected = ApiError.of(QUOTE_ALREADY_LOCKED, "Conflict", ex.getMessage());
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    void shouldHandlePoolNotFoundException() {
        // given
        var poolId = UUID.randomUUID();
        var ex = PoolNotFoundException.withId(poolId);

        // when
        var result = handler.handlePoolNotFound(ex);

        // then
        var expected = ApiError.of(LOCK_NOT_FOUND, "Not Found", ex.getMessage());
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    void shouldHandleInsufficientLiquidityException() {
        // given
        var ex = InsufficientLiquidityException.forCorridor("USD", "EUR",
                new BigDecimal("10000"), new BigDecimal("100"));

        // when
        var result = handler.handleInsufficientLiquidity(ex);

        // then
        var expected = ApiError.of(INSUFFICIENT_LIQUIDITY,
                "Unprocessable Entity", ex.getMessage());
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    void shouldHandleCorridorNotSupportedException() {
        // given
        var ex = CorridorNotSupportedException.forCurrencies("JPY", "BRL");

        // when
        var result = handler.handleCorridorNotSupported(ex);

        // then
        var expected = ApiError.of(CORRIDOR_NOT_SUPPORTED,
                "Unprocessable Entity", ex.getMessage());
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    void shouldHandleRateUnavailableException() {
        // given
        var ex = RateUnavailableException.forCorridor("USD", "EUR");

        // when
        var result = handler.handleRateUnavailable(ex);

        // then
        var expected = ApiError.of(RATE_UNAVAILABLE,
                "Service Unavailable", ex.getMessage());
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    void shouldHandleUnexpectedException() {
        // given
        var ex = new RuntimeException("unexpected");

        // when
        var result = handler.handleUnexpected(ex);

        // then
        var expected = ApiError.of(INTERNAL_ERROR,
                "Internal Server Error", "Internal Server Error");
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }
}
