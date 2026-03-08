package com.stablecoin.payments.fx.application.controller;

import com.stablecoin.payments.fx.api.response.ApiError;
import com.stablecoin.payments.fx.domain.exception.CorridorNotSupportedException;
import com.stablecoin.payments.fx.domain.exception.InsufficientLiquidityException;
import com.stablecoin.payments.fx.domain.exception.PoolNotFoundException;
import com.stablecoin.payments.fx.domain.exception.QuoteAlreadyLockedException;
import com.stablecoin.payments.fx.domain.exception.QuoteExpiredException;
import com.stablecoin.payments.fx.domain.exception.QuoteNotFoundException;
import com.stablecoin.payments.fx.domain.exception.RateUnavailableException;
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

import static com.stablecoin.payments.fx.application.controller.ErrorCodes.CORRIDOR_NOT_SUPPORTED;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.INSUFFICIENT_LIQUIDITY;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.INTERNAL_ERROR;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.LOCK_NOT_FOUND;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.QUOTE_ALREADY_LOCKED;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.QUOTE_EXPIRED;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.QUOTE_NOT_FOUND;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.RATE_UNAVAILABLE;
import static com.stablecoin.payments.fx.application.controller.ErrorCodes.VALIDATION_ERROR;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

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
        return ApiError.withErrors(VALIDATION_ERROR, BAD_REQUEST.getReasonPhrase(),
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
        return ApiError.withErrors(VALIDATION_ERROR, BAD_REQUEST.getReasonPhrase(),
                "Invalid request content", errors);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(QuoteNotFoundException.class)
    public ApiError handleQuoteNotFound(QuoteNotFoundException ex) {
        log.info("Quote not found: {}", ex.getMessage());
        return ApiError.of(QUOTE_NOT_FOUND, NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(GONE)
    @ExceptionHandler(QuoteExpiredException.class)
    public ApiError handleQuoteExpired(QuoteExpiredException ex) {
        log.info("Quote expired: {}", ex.getMessage());
        return ApiError.of(QUOTE_EXPIRED, GONE.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(QuoteAlreadyLockedException.class)
    public ApiError handleQuoteAlreadyLocked(QuoteAlreadyLockedException ex) {
        log.info("Quote already locked: {}", ex.getMessage());
        return ApiError.of(QUOTE_ALREADY_LOCKED, CONFLICT.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(PoolNotFoundException.class)
    public ApiError handlePoolNotFound(PoolNotFoundException ex) {
        log.info("Pool not found: {}", ex.getMessage());
        return ApiError.of(LOCK_NOT_FOUND, NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(UNPROCESSABLE_ENTITY)
    @ExceptionHandler(InsufficientLiquidityException.class)
    public ApiError handleInsufficientLiquidity(InsufficientLiquidityException ex) {
        log.info("Insufficient liquidity: {}", ex.getMessage());
        return ApiError.of(INSUFFICIENT_LIQUIDITY, UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(UNPROCESSABLE_ENTITY)
    @ExceptionHandler(CorridorNotSupportedException.class)
    public ApiError handleCorridorNotSupported(CorridorNotSupportedException ex) {
        log.info("Corridor not supported: {}", ex.getMessage());
        return ApiError.of(CORRIDOR_NOT_SUPPORTED, UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(SERVICE_UNAVAILABLE)
    @ExceptionHandler(RateUnavailableException.class)
    public ApiError handleRateUnavailable(RateUnavailableException ex) {
        log.info("Rate unavailable: {}", ex.getMessage());
        return ApiError.of(RATE_UNAVAILABLE, SERVICE_UNAVAILABLE.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiError handleUnexpected(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ApiError.of(INTERNAL_ERROR, INTERNAL_SERVER_ERROR.getReasonPhrase(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
}
