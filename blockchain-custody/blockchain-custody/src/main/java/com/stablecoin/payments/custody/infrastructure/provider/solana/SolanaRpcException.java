package com.stablecoin.payments.custody.infrastructure.provider.solana;

public class SolanaRpcException extends RuntimeException {

    public SolanaRpcException(String message) {
        super(message);
    }

    public SolanaRpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
