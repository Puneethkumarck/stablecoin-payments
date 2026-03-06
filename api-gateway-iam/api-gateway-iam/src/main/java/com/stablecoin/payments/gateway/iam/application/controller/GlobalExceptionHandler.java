package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.response.ApiError;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyExpiredException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyRevokedException;
import com.stablecoin.payments.gateway.iam.domain.exception.InvalidClientCredentialsException;
import com.stablecoin.payments.gateway.iam.domain.exception.IpNotAllowedException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantAccessDeniedException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotActiveException;
import com.stablecoin.payments.gateway.iam.domain.exception.MerchantNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.OAuthClientNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.RateLimitExceededException;
import com.stablecoin.payments.gateway.iam.domain.exception.ScopeExceededException;
import com.stablecoin.payments.gateway.iam.domain.exception.TokenRevokedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiError handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(ObjectError::getDefaultMessage, Collectors.toList())));
        log.info("Validation failed: {}", errors);
        return ApiError.withErrors("GW-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid request content", errors);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiError handleConstraintViolation(ConstraintViolationException ex) {
        var errors = ex.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        v -> v.getPropertyPath().toString(),
                        Collectors.mapping(jakarta.validation.ConstraintViolation::getMessage,
                                Collectors.toList())));
        return ApiError.withErrors("GW-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid request content", errors);
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler(MerchantAccessDeniedException.class)
    public ApiError handleMerchantAccessDenied(MerchantAccessDeniedException ex) {
        log.info("Merchant access denied: {}", ex.getMessage());
        return ApiError.of("GW-2003", FORBIDDEN.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(UNAUTHORIZED)
    @ExceptionHandler(InvalidClientCredentialsException.class)
    public ApiError handleInvalidCredentials(InvalidClientCredentialsException ex) {
        log.info("Invalid client credentials: {}", ex.getMessage());
        return ApiError.of("GW-1001", UNAUTHORIZED.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(MerchantNotFoundException.class)
    public ApiError handleMerchantNotFound(MerchantNotFoundException ex) {
        return ApiError.of("GW-2001", NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler(MerchantNotActiveException.class)
    public ApiError handleMerchantNotActive(MerchantNotActiveException ex) {
        return ApiError.of("GW-2002", FORBIDDEN.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(ApiKeyNotFoundException.class)
    public ApiError handleApiKeyNotFound(ApiKeyNotFoundException ex) {
        return ApiError.of("GW-3001", NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler(ApiKeyRevokedException.class)
    public ApiError handleApiKeyRevoked(ApiKeyRevokedException ex) {
        return ApiError.of("GW-3002", FORBIDDEN.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler(ApiKeyExpiredException.class)
    public ApiError handleApiKeyExpired(ApiKeyExpiredException ex) {
        return ApiError.of("GW-3003", FORBIDDEN.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler(IpNotAllowedException.class)
    public ApiError handleIpNotAllowed(IpNotAllowedException ex) {
        return ApiError.of("GW-3004", FORBIDDEN.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler(ScopeExceededException.class)
    public ApiError handleScopeExceeded(ScopeExceededException ex) {
        return ApiError.of("GW-3005", FORBIDDEN.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(TokenRevokedException.class)
    public ApiError handleTokenRevoked(TokenRevokedException ex) {
        return ApiError.of("GW-4001", BAD_REQUEST.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(OAuthClientNotFoundException.class)
    public ApiError handleOAuthClientNotFound(OAuthClientNotFoundException ex) {
        return ApiError.of("GW-5001", NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(TOO_MANY_REQUESTS)
    @ExceptionHandler(RateLimitExceededException.class)
    public ApiError handleRateLimitExceeded(RateLimitExceededException ex) {
        return ApiError.of("GW-6001", TOO_MANY_REQUESTS.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiError handleUnexpected(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ApiError.of("GW-9999", INTERNAL_SERVER_ERROR.getReasonPhrase(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
}
