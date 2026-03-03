package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.InvalidClientCredentialsException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotActiveException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("should handle invalid client credentials as 401")
    void shouldHandleInvalidCredentials() {
        var error = handler.handleInvalidCredentials(InvalidClientCredentialsException.clientNotFound());

        assertThat(error.code()).isEqualTo("GW-1001");
        assertThat(error.status()).isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("should handle merchant not found as 404")
    void shouldHandleMerchantNotFound() {
        var error = handler.handleMerchantNotFound(MerchantNotFoundException.byId(UUID.randomUUID()));

        assertThat(error.code()).isEqualTo("GW-2001");
        assertThat(error.status()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("should handle merchant not active as 403")
    void shouldHandleMerchantNotActive() {
        var error = handler.handleMerchantNotActive(MerchantNotActiveException.of(UUID.randomUUID()));

        assertThat(error.code()).isEqualTo("GW-2002");
        assertThat(error.status()).isEqualTo("Forbidden");
    }

    @Test
    @DisplayName("should handle api key not found as 404")
    void shouldHandleApiKeyNotFound() {
        var error = handler.handleApiKeyNotFound(ApiKeyNotFoundException.byId(UUID.randomUUID()));

        assertThat(error.code()).isEqualTo("GW-3001");
        assertThat(error.status()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("should handle rate limit exceeded as 429")
    void shouldHandleRateLimitExceeded() {
        var error = handler.handleRateLimitExceeded(RateLimitExceededException.perMinute(UUID.randomUUID(), 60));

        assertThat(error.code()).isEqualTo("GW-6001");
        assertThat(error.status()).isEqualTo("Too Many Requests");
    }

    @Test
    @DisplayName("should handle unexpected error as 500")
    void shouldHandleUnexpectedError() {
        var error = handler.handleUnexpected(new RuntimeException("oops"));

        assertThat(error.code()).isEqualTo("GW-9999");
        assertThat(error.status()).isEqualTo("Internal Server Error");
    }
}
