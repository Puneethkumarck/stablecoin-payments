package com.stablecoin.payments.custody.application.controller;

import com.stablecoin.payments.custody.api.ApiError;
import com.stablecoin.payments.custody.domain.exception.ChainUnavailableException;
import com.stablecoin.payments.custody.domain.exception.CustodySigningException;
import com.stablecoin.payments.custody.domain.exception.InsufficientBalanceException;
import com.stablecoin.payments.custody.domain.exception.TransferNotFoundException;
import com.stablecoin.payments.custody.domain.exception.WalletNotFoundException;
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
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * Global exception handler for the Blockchain & Custody service.
 * Maps domain exceptions to appropriate HTTP responses with BC-XXXX error codes.
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
        return ApiError.withErrors("BC-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid request content", errors);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiError handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.info("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        return ApiError.of("BC-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ResponseStatus(UNPROCESSABLE_ENTITY)
    @ExceptionHandler(InsufficientBalanceException.class)
    public ApiError handleInsufficientBalance(InsufficientBalanceException ex) {
        log.info("Insufficient balance: {}", ex.getMessage());
        return ApiError.of(InsufficientBalanceException.ERROR_CODE,
                UNPROCESSABLE_ENTITY.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(SERVICE_UNAVAILABLE)
    @ExceptionHandler(ChainUnavailableException.class)
    public ApiError handleChainUnavailable(ChainUnavailableException ex) {
        log.warn("Chain unavailable: {}", ex.getMessage());
        return ApiError.of(ChainUnavailableException.ERROR_CODE,
                SERVICE_UNAVAILABLE.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(TransferNotFoundException.class)
    public ApiError handleTransferNotFound(TransferNotFoundException ex) {
        log.info("Transfer not found: {}", ex.getMessage());
        return ApiError.of(TransferNotFoundException.ERROR_CODE,
                NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(WalletNotFoundException.class)
    public ApiError handleWalletNotFound(WalletNotFoundException ex) {
        log.info("Wallet not found: {}", ex.getMessage());
        return ApiError.of(WalletNotFoundException.ERROR_CODE,
                NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(CustodySigningException.class)
    public ApiError handleCustodySigning(CustodySigningException ex) {
        log.error("Custody signing error: {}", ex.getClass().getSimpleName());
        return ApiError.of(CustodySigningException.ERROR_CODE,
                INTERNAL_SERVER_ERROR.getReasonPhrase(), "Custody signing failed");
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(IllegalStateException.class)
    public ApiError handleIllegalState(IllegalStateException ex) {
        log.info("Invalid state transition: {}", ex.getMessage());
        return ApiError.of("BC-0002", CONFLICT.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiError handleIllegalArgument(IllegalArgumentException ex) {
        log.info("Illegal argument: {}", ex.getMessage());
        return ApiError.of("BC-0001", BAD_REQUEST.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiError handleUnexpected(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ApiError.of("BC-9999", INTERNAL_SERVER_ERROR.getReasonPhrase(),
                INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
}
