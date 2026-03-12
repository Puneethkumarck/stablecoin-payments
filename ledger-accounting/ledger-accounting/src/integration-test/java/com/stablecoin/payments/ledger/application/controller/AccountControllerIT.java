package com.stablecoin.payments.ledger.application.controller;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.domain.port.AccountBalanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CLIENT_FUNDS_HELD;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FIAT_RECEIVABLE;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AccountController IT")
class AccountControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    @Nested
    @DisplayName("GET /v1/ledger/accounts/{accountCode}/balance")
    class GetAccountBalance {

        @Test
        @DisplayName("should return 200 with multi-currency balances")
        void shouldReturn200WithMultiCurrencyBalances() throws Exception {
            accountBalanceRepository.save(new AccountBalance(
                    FIAT_RECEIVABLE, "USD", new BigDecimal("50000.00"), 5L,
                    UUID.randomUUID(), Instant.now()));
            accountBalanceRepository.save(new AccountBalance(
                    FIAT_RECEIVABLE, "EUR", new BigDecimal("30000.00"), 3L,
                    UUID.randomUUID(), Instant.now()));

            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/balance", FIAT_RECEIVABLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCode", is(FIAT_RECEIVABLE)))
                    .andExpect(jsonPath("$.accountName", is("Fiat Receivable")))
                    .andExpect(jsonPath("$.accountType", is("ASSET")))
                    .andExpect(jsonPath("$.balances", hasSize(2)))
                    .andExpect(jsonPath("$.asOf", notNullValue()));
        }

        @Test
        @DisplayName("should return 200 with empty balances when account has no balance records")
        void shouldReturn200WithEmptyBalances() throws Exception {
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/balance", CLIENT_FUNDS_HELD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCode", is(CLIENT_FUNDS_HELD)))
                    .andExpect(jsonPath("$.accountName", is("Client Funds Held")))
                    .andExpect(jsonPath("$.accountType", is("LIABILITY")))
                    .andExpect(jsonPath("$.balances", hasSize(0)));
        }

        @Test
        @DisplayName("should return 404 for non-existent account code")
        void shouldReturn404ForNonExistentAccount() throws Exception {
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/balance", "9999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("LD-1002")));
        }
    }

    @Nested
    @DisplayName("GET /v1/ledger/trial-balance")
    class GetTrialBalance {

        @Test
        @DisplayName("should return 200 with all seeded accounts and balanced totals")
        void shouldReturn200WithTrialBalance() throws Exception {
            mockMvc.perform(get("/v1/ledger/trial-balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.asOf", notNullValue()))
                    .andExpect(jsonPath("$.accounts", hasSize(greaterThanOrEqualTo(10))))
                    .andExpect(jsonPath("$.balanced", is(true)));
        }

        @Test
        @DisplayName("should return 200 with correct debit/credit classification when balances exist")
        void shouldReturn200WithCorrectClassification() throws Exception {
            accountBalanceRepository.save(new AccountBalance(
                    FIAT_RECEIVABLE, "USD", new BigDecimal("10000.00"), 1L,
                    UUID.randomUUID(), Instant.now()));
            accountBalanceRepository.save(new AccountBalance(
                    CLIENT_FUNDS_HELD, "USD", new BigDecimal("10000.00"), 1L,
                    UUID.randomUUID(), Instant.now()));

            mockMvc.perform(get("/v1/ledger/trial-balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balanced", is(true)));
        }
    }
}
