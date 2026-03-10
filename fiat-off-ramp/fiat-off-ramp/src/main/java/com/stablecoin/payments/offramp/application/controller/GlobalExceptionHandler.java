package com.stablecoin.payments.offramp.application.controller;

import com.stablecoin.payments.offramp.api.ApiError;
import com.stablecoin.payments.offramp.domain.exception.PayoutNotFoundException;
import com.stablecoin.payments.offramp.domain.exception.PayoutNotRefundableException;
import com.stablecoin.payments.offramp.domain.exception.PayoutPartnerException;
import com.stablecoin.payments.offramp.domain.exception.RedemptionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * Global exception handler for the Fiat Off-Ramp service.
 * Maps domain exceptions to appropriate HTTP responses with error codes.
 */
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
        return ApiError.withErrors("OF-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid request content", errors);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiError handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.info("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        return ApiError.of("OF-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(PayoutNotFoundException.class)
    public ApiError handlePayoutNotFound(PayoutNotFoundException ex) {
        log.info("Payout not found: {}", ex.getMessage());
        return ApiError.of(PayoutNotFoundException.ERROR_CODE, NOT_FOUND.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(UNPROCESSABLE_ENTITY)
    @ExceptionHandler(RedemptionFailedException.class)
    public ApiError handleRedemptionFailed(RedemptionFailedException ex) {
        log.warn("Redemption failed: {}", ex.getMessage());
        return ApiError.of(RedemptionFailedException.ERROR_CODE, UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(BAD_GATEWAY)
    @ExceptionHandler(PayoutPartnerException.class)
    public ApiError handlePayoutPartnerError(PayoutPartnerException ex) {
        log.warn("Payout partner error: {}", ex.getMessage());
        return ApiError.of(PayoutPartnerException.ERROR_CODE, BAD_GATEWAY.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(PayoutNotRefundableException.class)
    public ApiError handlePayoutNotRefundable(PayoutNotRefundableException ex) {
        log.info("Payout not refundable: {}", ex.getMessage());
        return ApiError.of(PayoutNotRefundableException.ERROR_CODE, CONFLICT.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(IllegalStateException.class)
    public ApiError handleIllegalState(IllegalStateException ex) {
        log.info("Invalid state transition: {}", ex.getMessage());
        return ApiError.of("OF-0002", CONFLICT.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiError handleIllegalArgument(IllegalArgumentException ex) {
        log.info("Illegal argument: {}", ex.getMessage());
        return ApiError.of("OF-0001", BAD_REQUEST.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiError handleUnexpected(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ApiError.of("OF-9999", INTERNAL_SERVER_ERROR.getReasonPhrase(),
                INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
}
