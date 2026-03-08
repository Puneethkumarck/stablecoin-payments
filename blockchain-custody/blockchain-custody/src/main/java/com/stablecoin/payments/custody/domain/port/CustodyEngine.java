package com.stablecoin.payments.custody.domain.port;

public interface CustodyEngine {

    SignResult signAndSubmit(SignRequest request);

    TransactionStatus getTransactionStatus(String txId);
}
