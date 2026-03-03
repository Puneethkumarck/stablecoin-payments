package com.stablecoin.payments.gateway.iam.domain.exception;

import java.util.List;

public class ScopeExceededException extends RuntimeException {

    private ScopeExceededException(String message) {
        super(message);
    }

    public static ScopeExceededException of(List<String> requested, List<String> allowed) {
        return new ScopeExceededException(
                "Requested scopes " + requested + " exceed allowed scopes " + allowed);
    }
}
