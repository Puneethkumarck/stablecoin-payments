package com.stablecoin.payments.ledger.application.controller;

import com.stablecoin.payments.ledger.api.AccountBalanceResponse;
import com.stablecoin.payments.ledger.api.AccountBalanceResponse.CurrencyBalance;
import com.stablecoin.payments.ledger.api.TrialBalanceResponse;
import com.stablecoin.payments.ledger.api.TrialBalanceResponse.AccountLine;
import com.stablecoin.payments.ledger.domain.service.LedgerQueryHandler;
import com.stablecoin.payments.ledger.domain.service.LedgerQueryHandler.AccountWithBalances;
import com.stablecoin.payments.ledger.domain.service.LedgerQueryHandler.TrialBalance;
import com.stablecoin.payments.ledger.domain.service.LedgerQueryHandler.TrialBalanceLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/v1/ledger")
@RequiredArgsConstructor
public class AccountController {

    private final LedgerQueryHandler queryHandler;
    private final Clock clock;

    @GetMapping("/accounts/{accountCode}/balance")
    public AccountBalanceResponse getAccountBalance(@PathVariable String accountCode) {
        log.info("GET /v1/ledger/accounts/{}/balance", accountCode);
        var result = queryHandler.getAccountBalance(accountCode);
        return toAccountBalanceResponse(result);
    }

    @GetMapping("/trial-balance")
    public TrialBalanceResponse getTrialBalance() {
        log.info("GET /v1/ledger/trial-balance");
        var result = queryHandler.getTrialBalance();
        return toTrialBalanceResponse(result);
    }

    private AccountBalanceResponse toAccountBalanceResponse(AccountWithBalances result) {
        var balances = result.balances().stream()
                .map(b -> new CurrencyBalance(
                        b.currency(),
                        b.balance(),
                        b.version()))
                .toList();
        return new AccountBalanceResponse(
                result.account().accountCode(),
                result.account().accountName(),
                result.account().accountType().name(),
                balances,
                Instant.now(clock)
        );
    }

    private TrialBalanceResponse toTrialBalanceResponse(TrialBalance result) {
        var lines = result.lines().stream()
                .map(this::toAccountLine)
                .toList();
        return new TrialBalanceResponse(
                Instant.now(clock),
                lines,
                result.totalDebits(),
                result.totalCredits(),
                result.balanced()
        );
    }

    private AccountLine toAccountLine(TrialBalanceLine line) {
        return new AccountLine(
                line.account().accountCode(),
                line.account().accountName(),
                line.account().accountType().name(),
                line.debitBalance(),
                line.creditBalance()
        );
    }
}
