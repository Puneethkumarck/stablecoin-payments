package com.stablecoin.payments.ledger.client;

import com.stablecoin.payments.ledger.api.AccountBalanceResponse;
import com.stablecoin.payments.ledger.api.AccountHistoryResponse;
import com.stablecoin.payments.ledger.api.PaymentJournalResponse;
import com.stablecoin.payments.ledger.api.ReconciliationResponse;
import com.stablecoin.payments.ledger.api.TrialBalanceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "ledger-accounting-service", url = "${app.services.ledger-accounting.url}")
public interface LedgerClient {

    @GetMapping(value = "/v1/ledger/payments/{paymentId}/journal", produces = "application/json")
    PaymentJournalResponse getPaymentJournal(@PathVariable("paymentId") UUID paymentId);

    @GetMapping(value = "/v1/reconciliation/{paymentId}", produces = "application/json")
    ReconciliationResponse getReconciliation(@PathVariable("paymentId") UUID paymentId);

    @GetMapping(value = "/v1/ledger/accounts/{accountCode}/balance", produces = "application/json")
    AccountBalanceResponse getAccountBalance(@PathVariable("accountCode") String accountCode);

    @GetMapping(value = "/v1/ledger/accounts/{accountCode}/history", produces = "application/json")
    AccountHistoryResponse getAccountHistory(@PathVariable("accountCode") String accountCode,
                                             @RequestParam("currency") String currency);

    @GetMapping(value = "/v1/ledger/trial-balance", produces = "application/json")
    TrialBalanceResponse getTrialBalance();
}
