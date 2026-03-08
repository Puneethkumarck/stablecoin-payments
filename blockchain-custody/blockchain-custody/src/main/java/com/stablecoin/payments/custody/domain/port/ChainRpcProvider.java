package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.ChainId;

import java.math.BigDecimal;

public interface ChainRpcProvider {

    TransactionReceipt getTransactionReceipt(ChainId chainId, String txHash);

    long getLatestBlockNumber(ChainId chainId);

    BigDecimal getTokenBalance(ChainId chainId, String address, String tokenContract);
}
