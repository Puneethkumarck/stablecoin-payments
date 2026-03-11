package com.stablecoin.payments.ledger.domain.exception;

public class AccountNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "LD-1002";

    public AccountNotFoundException(String accountCode) {
        super("Account not found: " + accountCode);
    }

    public String errorCode() {
        return ERROR_CODE;
    }
}
