package com.stablecoin.payments.ledger.application.controller;

import com.stablecoin.payments.ledger.api.ApiError;
import com.stablecoin.payments.ledger.domain.exception.AccountNotFoundException;
import com.stablecoin.payments.ledger.domain.exception.DuplicateTransactionException;
import com.stablecoin.payments.ledger.domain.exception.JournalNotFoundException;
import com.stablecoin.payments.ledger.domain.exception.ReconciliationNotFoundException;
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
        return ApiError.withErrors("LD-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid request content", errors);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiError handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.info("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        return ApiError.of("LD-0001", BAD_REQUEST.getReasonPhrase(),
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(JournalNotFoundException.class)
    public ApiError handleJournalNotFound(JournalNotFoundException ex) {
        log.info("Journal not found: {}", ex.getMessage());
        return ApiError.of(ex.errorCode(), NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(AccountNotFoundException.class)
    public ApiError handleAccountNotFound(AccountNotFoundException ex) {
        log.info("Account not found: {}", ex.getMessage());
        return ApiError.of(ex.errorCode(), NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(ReconciliationNotFoundException.class)
    public ApiError handleReconciliationNotFound(ReconciliationNotFoundException ex) {
        log.info("Reconciliation not found: {}", ex.getMessage());
        return ApiError.of(ex.errorCode(), NOT_FOUND.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(DuplicateTransactionException.class)
    public ApiError handleDuplicateTransaction(DuplicateTransactionException ex) {
        log.info("Duplicate transaction: {}", ex.getMessage());
        return ApiError.of(ex.errorCode(), CONFLICT.getReasonPhrase(), ex.getMessage());
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiError handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ApiError.of("LD-0001", BAD_REQUEST.getReasonPhrase(), "Invalid request parameter");
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiError handleUnexpected(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ApiError.of("LD-9999", INTERNAL_SERVER_ERROR.getReasonPhrase(),
                INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
}
