package com.stablecoin.payments.merchant.onboarding.application.controller;

import com.stablecoin.payments.merchant.onboarding.domain.exceptions.InvalidMerchantStateException;
import com.stablecoin.payments.merchant.onboarding.domain.exceptions.MerchantAlreadyExistsException;
import com.stablecoin.payments.merchant.onboarding.domain.exceptions.MerchantNotFoundException;
import com.stablecoin.payments.merchant.onboarding.domain.statemachine.StateMachineException;
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

import static com.stablecoin.payments.merchant.onboarding.application.controller.ErrorCodes.BAD_REQUEST_CODE;
import static com.stablecoin.payments.merchant.onboarding.application.controller.ErrorCodes.INTERNAL_ERROR_CODE;
import static com.stablecoin.payments.merchant.onboarding.application.controller.ErrorCodes.INVALID_STATE_CODE;
import static com.stablecoin.payments.merchant.onboarding.application.controller.ErrorCodes.MERCHANT_ALREADY_EXISTS_CODE;
import static com.stablecoin.payments.merchant.onboarding.application.controller.ErrorCodes.MERCHANT_NOT_FOUND_CODE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Validation ───────────────────────────────────────────────────────────

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiError handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(ObjectError::getDefaultMessage, Collectors.toList())));
        log.info("Validation failed: {}", errors);
        return ApiError.withErrors(BAD_REQUEST_CODE, BAD_REQUEST.getReasonPhrase(),
                "Invalid request content", errors);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiError handleConstraintViolation(ConstraintViolationException ex) {
        var errors = ex.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        v -> v.getPropertyPath().toString(),
                        Collectors.mapping(v -> v.getMessage(), Collectors.toList())));
        log.info("Constraint violation: {}", errors);
        return ApiError.withErrors(BAD_REQUEST_CODE, BAD_REQUEST.getReasonPhrase(),
                "Invalid request content", errors);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiError handleIllegalArgument(IllegalArgumentException ex) {
        log.info("Illegal argument: {}", ex.getMessage());
        return ApiError.of(BAD_REQUEST_CODE, BAD_REQUEST.getReasonPhrase(), ex.getMessage());
    }

    // ── 404 ──────────────────────────────────────────────────────────────────

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(MerchantNotFoundException.class)
    public ApiError handleNotFound(MerchantNotFoundException ex) {
        log.info("Merchant not found: {}", ex.getMessage());
        return ApiError.of(MERCHANT_NOT_FOUND_CODE, NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    // ── 409 ──────────────────────────────────────────────────────────────────

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(MerchantAlreadyExistsException.class)
    public ApiError handleAlreadyExists(MerchantAlreadyExistsException ex) {
        log.info("Merchant already exists: {}", ex.getMessage());
        return ApiError.of(MERCHANT_ALREADY_EXISTS_CODE, CONFLICT.getReasonPhrase(), ex.getMessage());
    }

    // ── 422 ──────────────────────────────────────────────────────────────────

    @ResponseStatus(UNPROCESSABLE_ENTITY)
    @ExceptionHandler({InvalidMerchantStateException.class, StateMachineException.class,
            IllegalStateException.class})
    public ApiError handleInvalidState(RuntimeException ex) {
        log.info("Invalid merchant state: {}", ex.getMessage());
        return ApiError.of(INVALID_STATE_CODE, UNPROCESSABLE_ENTITY.getReasonPhrase(), ex.getMessage());
    }

    // ── 500 ──────────────────────────────────────────────────────────────────

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiError handleUnexpected(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ApiError.of(INTERNAL_ERROR_CODE, INTERNAL_SERVER_ERROR.getReasonPhrase(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
}
