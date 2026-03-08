package com.stablecoin.payments.custody.infrastructure.provider.fireblocks;

record FireblocksTransactionStatusResponse(
        String id,
        String status,
        String txHash,
        Integer numOfConfirmations
) {}
