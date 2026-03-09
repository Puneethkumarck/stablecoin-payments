package com.stablecoin.payments.custody.application.controller;

import com.stablecoin.payments.custody.config.SecurityConfig;
import com.stablecoin.payments.custody.domain.exception.ChainUnavailableException;
import com.stablecoin.payments.custody.domain.exception.CustodySigningException;
import com.stablecoin.payments.custody.domain.exception.InsufficientBalanceException;
import com.stablecoin.payments.custody.domain.exception.TransferNotFoundException;
import com.stablecoin.payments.custody.domain.exception.WalletNotFoundException;
import com.stablecoin.payments.custody.domain.model.TransferResult;
import com.stablecoin.payments.custody.domain.model.TransferType;
import com.stablecoin.payments.custody.domain.service.TransferCommandHandler;
import com.stablecoin.payments.custody.domain.service.TransferCommandHandler.WalletBalanceDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.AMOUNT;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.CORRELATION_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.PAYMENT_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TO_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aSubmittedTransfer;
import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.aBalanceWith;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.anActiveWallet;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransferController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.security.enabled=false")
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferCommandHandler transferCommandHandler;

    @Nested
    @DisplayName("POST /v1/transfers")
    class SubmitTransfer {

        @Test
        @DisplayName("should return 202 Accepted for new transfer")
        void shouldReturn202ForNewTransfer() throws Exception {
            var transfer = aSubmittedTransfer();
            var result = new TransferResult(transfer, true);

            given(transferCommandHandler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, "base"))
                    .willReturn(result);

            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "paymentId": "%s",
                                      "correlationId": "%s",
                                      "transferType": "FORWARD",
                                      "stablecoin": "USDC",
                                      "amount": "1000.00",
                                      "toWalletAddress": "%s",
                                      "preferredChain": "base"
                                    }
                                    """.formatted(PAYMENT_ID, CORRELATION_ID, TO_ADDRESS)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.transferId").value(transfer.transferId().toString()))
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.chainId").value("base"));
        }

        @Test
        @DisplayName("should return 200 OK for idempotent replay")
        void shouldReturn200ForIdempotentReplay() throws Exception {
            var transfer = aSubmittedTransfer();
            var result = new TransferResult(transfer, false);

            given(transferCommandHandler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, "base"))
                    .willReturn(result);

            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "paymentId": "%s",
                                      "correlationId": "%s",
                                      "transferType": "FORWARD",
                                      "stablecoin": "USDC",
                                      "amount": "1000.00",
                                      "toWalletAddress": "%s",
                                      "preferredChain": "base"
                                    }
                                    """.formatted(PAYMENT_ID, CORRELATION_ID, TO_ADDRESS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transferId").value(transfer.transferId().toString()));
        }

        @Test
        @DisplayName("should return 400 for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BC-0001"));
        }

        @Test
        @DisplayName("should return 422 for insufficient balance")
        void shouldReturn422ForInsufficientBalance() throws Exception {
            given(transferCommandHandler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, "base"))
                    .willThrow(new InsufficientBalanceException("Insufficient balance"));

            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "paymentId": "%s",
                                      "correlationId": "%s",
                                      "transferType": "FORWARD",
                                      "stablecoin": "USDC",
                                      "amount": "1000.00",
                                      "toWalletAddress": "%s",
                                      "preferredChain": "base"
                                    }
                                    """.formatted(PAYMENT_ID, CORRELATION_ID, TO_ADDRESS)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BC-1001"));
        }

        @Test
        @DisplayName("should return 500 for custody signing failure")
        void shouldReturn500ForCustodySigningFailure() throws Exception {
            given(transferCommandHandler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, "base"))
                    .willThrow(new CustodySigningException("MPC signing timeout"));

            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "paymentId": "%s",
                                      "correlationId": "%s",
                                      "transferType": "FORWARD",
                                      "stablecoin": "USDC",
                                      "amount": "1000.00",
                                      "toWalletAddress": "%s",
                                      "preferredChain": "base"
                                    }
                                    """.formatted(PAYMENT_ID, CORRELATION_ID, TO_ADDRESS)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("BC-1004"));
        }

        @Test
        @DisplayName("should return 503 when chain unavailable")
        void shouldReturn503ForChainUnavailable() throws Exception {
            given(transferCommandHandler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, "base"))
                    .willThrow(new ChainUnavailableException("No healthy chain available"));

            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "paymentId": "%s",
                                      "correlationId": "%s",
                                      "transferType": "FORWARD",
                                      "stablecoin": "USDC",
                                      "amount": "1000.00",
                                      "toWalletAddress": "%s",
                                      "preferredChain": "base"
                                    }
                                    """.formatted(PAYMENT_ID, CORRELATION_ID, TO_ADDRESS)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("BC-1002"));
        }
    }

    @Nested
    @DisplayName("GET /v1/transfers/{transferId}")
    class GetTransfer {

        @Test
        @DisplayName("should return transfer details")
        void shouldReturnTransferDetails() throws Exception {
            var transfer = aSubmittedTransfer();
            given(transferCommandHandler.getTransfer(transfer.transferId()))
                    .willReturn(transfer);

            mockMvc.perform(get("/v1/transfers/{transferId}", transfer.transferId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transferId").value(transfer.transferId().toString()))
                    .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                    .andExpect(jsonPath("$.status").value("SUBMITTED"));
        }

        @Test
        @DisplayName("should return 404 when transfer not found")
        void shouldReturn404WhenNotFound() throws Exception {
            var transferId = UUID.randomUUID();
            given(transferCommandHandler.getTransfer(transferId))
                    .willThrow(new TransferNotFoundException("Transfer not found: " + transferId));

            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BC-1003"));
        }

        @Test
        @DisplayName("should return 400 for invalid UUID")
        void shouldReturn400ForInvalidUuid() throws Exception {
            mockMvc.perform(get("/v1/transfers/{transferId}", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BC-0001"));
        }
    }

    @Nested
    @DisplayName("GET /v1/wallets/{walletId}/balance")
    class GetWalletBalance {

        @Test
        @DisplayName("should return wallet balance details")
        void shouldReturnWalletBalanceDetails() throws Exception {
            var wallet = anActiveWallet();
            var balance = aBalanceWith(new BigDecimal("5000.00"), new BigDecimal("1000.00"));
            var details = new WalletBalanceDetails(wallet, List.of(balance));

            given(transferCommandHandler.getWalletBalance(wallet.walletId()))
                    .willReturn(details);

            mockMvc.perform(get("/v1/wallets/{walletId}/balance", wallet.walletId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.walletId").value(wallet.walletId().toString()))
                    .andExpect(jsonPath("$.address").value(wallet.address()))
                    .andExpect(jsonPath("$.chainId").value("base"))
                    .andExpect(jsonPath("$.balances[0].stablecoin").value("USDC"))
                    .andExpect(jsonPath("$.balances[0].availableBalance").value("5000.00"))
                    .andExpect(jsonPath("$.balances[0].reservedBalance").value("1000.00"));
        }

        @Test
        @DisplayName("should return 404 when wallet not found")
        void shouldReturn404WhenWalletNotFound() throws Exception {
            var walletId = UUID.randomUUID();
            given(transferCommandHandler.getWalletBalance(walletId))
                    .willThrow(new WalletNotFoundException("Wallet not found: " + walletId));

            mockMvc.perform(get("/v1/wallets/{walletId}/balance", walletId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BC-1005"));
        }
    }
}
