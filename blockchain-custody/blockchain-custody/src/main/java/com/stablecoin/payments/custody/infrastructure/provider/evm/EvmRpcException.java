package com.stablecoin.payments.custody.infrastructure.provider.evm;

public class EvmRpcException extends RuntimeException {

    public EvmRpcException(String message) {
        super(message);
    }

    public EvmRpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
