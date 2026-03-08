package com.stablecoin.payments.custody.domain.port;

public record TransactionStatus(String status, String txHash, Integer confirmations) {}
