package com.stablecoin.payments.custody.client;

import com.stablecoin.payments.custody.api.TransferResponse;
import com.stablecoin.payments.custody.api.WalletBalanceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "blockchain-custody-service", url = "${app.services.blockchain-custody.url}")
public interface BlockchainCustodyClient {

    @GetMapping(value = "/v1/transfers/{transferId}", produces = "application/json")
    TransferResponse getTransfer(@PathVariable("transferId") UUID transferId);

    @GetMapping(value = "/v1/wallets/{walletId}/balance", produces = "application/json")
    WalletBalanceResponse getWalletBalance(@PathVariable("walletId") UUID walletId);
}
