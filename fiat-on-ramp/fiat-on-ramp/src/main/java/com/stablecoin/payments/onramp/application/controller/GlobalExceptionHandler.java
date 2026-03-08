package com.stablecoin.payments.onramp.application.controller;

import com.stablecoin.payments.onramp.api.ApiError;
import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.exception.RefundAmountExceededException;
import com.stablecoin.payments.onramp.domain.exception.RefundNotAllowedException;
import com.stablecoin.payments.onramp.domain.exception.RefundNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * Global exception handler for the Fiat On-Ramp service.
 * <p>
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
        return ApiError.withErrors("OR-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid request content", errors);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiError handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.info("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        return ApiError.of("OR-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(CollectionOrderNotFoundException.class)
    public ApiError handleCollectionNotFound(CollectionOrderNotFoundException ex) {
        log.info("Collection order not found: {}", ex.getMessage());
        return ApiError.of(CollectionOrderNotFoundException.ERROR_CODE, NOT_FOUND.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(RefundNotFoundException.class)
    public ApiError handleRefundNotFound(RefundNotFoundException ex) {
        log.info("Refund not found: {}", ex.getMessage());
        return ApiError.of(RefundNotFoundException.ERROR_CODE, NOT_FOUND.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(RefundNotAllowedException.class)
    public ApiError handleRefundNotAllowed(RefundNotAllowedException ex) {
        log.info("Refund not allowed: {}", ex.getMessage());
        return ApiError.of(RefundNotAllowedException.ERROR_CODE, CONFLICT.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(UNPROCESSABLE_ENTITY)
    @ExceptionHandler(RefundAmountExceededException.class)
    public ApiError handleRefundAmountExceeded(RefundAmountExceededException ex) {
        log.info("Refund amount exceeded: {}", ex.getMessage());
        return ApiError.of(RefundAmountExceededException.ERROR_CODE, UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(IllegalStateException.class)
    public ApiError handleIllegalState(IllegalStateException ex) {
        log.info("Invalid state transition: {}", ex.getMessage());
        return ApiError.of("OR-0002", CONFLICT.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiError handleIllegalArgument(IllegalArgumentException ex) {
        log.info("Illegal argument: {}", ex.getMessage());
        return ApiError.of("OR-0001", BAD_REQUEST.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiError handleUnexpected(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ApiError.of("OR-9999", INTERNAL_SERVER_ERROR.getReasonPhrase(),
                INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
}
