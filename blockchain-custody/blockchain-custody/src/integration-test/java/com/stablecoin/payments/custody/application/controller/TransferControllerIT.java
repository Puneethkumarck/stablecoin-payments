package com.stablecoin.payments.custody.application.controller;

import com.stablecoin.payments.custody.AbstractIntegrationTest;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.TransferType;
import com.stablecoin.payments.custody.domain.model.WalletBalance;
import com.stablecoin.payments.custody.domain.port.ChainTransferRepository;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.AMOUNT;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.CORRELATION_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.FROM_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.PAYMENT_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TO_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TX_HASH;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aTransferRequestJson;
import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.anActiveWallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TransferController IT")
class TransferControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    @Autowired
    private ChainTransferRepository chainTransferRepository;

    @Nested
    @DisplayName("POST /v1/transfers")
    class SubmitTransfer {

        @Test
        @DisplayName("should return 202 Accepted for new transfer with real DB pipeline")
        void shouldReturn202ForNewTransfer() throws Exception {
            // given
            var wallet = walletRepository.save(anActiveWallet());
            var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC)
                    .syncFromChain(new BigDecimal("50000.00"), 100L);
            walletBalanceRepository.save(balance);

            // when/then
            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aTransferRequestJson()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.transferId", notNullValue()))
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())))
                    .andExpect(jsonPath("$.status", is("SUBMITTED")))
                    .andExpect(jsonPath("$.chainId", is("base")))
                    .andExpect(jsonPath("$.stablecoin", is("USDC")))
                    .andExpect(jsonPath("$.amount", is("1000.00")))
                    .andExpect(jsonPath("$.txHash", notNullValue()));
        }

        @Test
        @DisplayName("should return 200 OK for idempotent replay with same paymentId")
        void shouldReturn200ForIdempotentReplay() throws Exception {
            // given
            var wallet = walletRepository.save(anActiveWallet());
            var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC)
                    .syncFromChain(new BigDecimal("50000.00"), 100L);
            walletBalanceRepository.save(balance);
            var mapper = JsonMapper.builder().build();

            // first request — 202 Accepted
            var firstResponse = mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aTransferRequestJson()))
                    .andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            var firstTransferId = mapper.readTree(firstResponse).get("transferId").asText();

            // when — second request with same paymentId
            var secondResponse = mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aTransferRequestJson()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var secondTransferId = mapper.readTree(secondResponse).get("transferId").asText();

            // then — same transferId returned, no duplicate created
            assertThat(secondTransferId).isEqualTo(firstTransferId);
            assertThat(chainTransferRepository.findByPaymentIdAndType(PAYMENT_ID, TransferType.FORWARD))
                    .isPresent();
        }

        @Test
        @DisplayName("should return 400 Bad Request for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            // given/when/then
            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("BC-0001")))
                    .andExpect(jsonPath("$.errors", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid amount format")
        void shouldReturn400ForInvalidAmountFormat() throws Exception {
            // given/when/then
            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "paymentId": "%s",
                                      "correlationId": "%s",
                                      "transferType": "FORWARD",
                                      "stablecoin": "USDC",
                                      "amount": "abc",
                                      "toWalletAddress": "%s",
                                      "preferredChain": "base"
                                    }
                                    """.formatted(PAYMENT_ID, CORRELATION_ID, TO_ADDRESS)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("BC-0001")));
        }

        @Test
        @DisplayName("should return 503 when no chain has sufficient wallet balance")
        void shouldReturn503ForInsufficientBalance() throws Exception {
            // given — wallet with only 100 USDC (request is for 1000)
            // Chain selection engine filters by liquidity, so no chain qualifies → 503
            var wallet = walletRepository.save(anActiveWallet());
            var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC)
                    .syncFromChain(new BigDecimal("100.00"), 100L);
            walletBalanceRepository.save(balance);

            // when/then
            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aTransferRequestJson()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code", is("BC-1002")));
        }

        @Test
        @DisplayName("should reserve balance after successful transfer submission")
        void shouldReserveBalanceAfterTransfer() throws Exception {
            // given
            var wallet = walletRepository.save(anActiveWallet());
            var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC)
                    .syncFromChain(new BigDecimal("5000.00"), 100L);
            walletBalanceRepository.save(balance);

            // when
            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aTransferRequestJson()))
                    .andExpect(status().isAccepted());

            // then — verify balance was reserved in DB
            var updatedBalance = walletBalanceRepository
                    .findByWalletIdAndStablecoin(wallet.walletId(), USDC)
                    .orElseThrow();
            var expected = balance.reserve(AMOUNT);
            assertThat(updatedBalance)
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .ignoringFieldsOfTypes(Instant.class)
                    .ignoringFields("version")
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("GET /v1/transfers/{transferId}")
    class GetTransfer {

        @Test
        @DisplayName("should return 200 OK with transfer details")
        void shouldReturn200ForExistingTransfer() throws Exception {
            // given — save wallet, then build transfer with matching walletId
            var wallet = walletRepository.save(anActiveWallet());
            var transfer = ChainTransfer.initiate(
                            PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                            USDC, AMOUNT, wallet.walletId(), TO_ADDRESS, FROM_ADDRESS)
                    .selectChain(CHAIN_BASE)
                    .startSigning(42L)
                    .submit(TX_HASH);
            transfer = chainTransferRepository.save(transfer);

            // when/then
            mockMvc.perform(get("/v1/transfers/{transferId}", transfer.transferId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transferId", is(transfer.transferId().toString())))
                    .andExpect(jsonPath("$.paymentId", is(PAYMENT_ID.toString())))
                    .andExpect(jsonPath("$.status", is("SUBMITTED")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing transfer")
        void shouldReturn404ForNonExistingTransfer() throws Exception {
            // given
            var transferId = UUID.randomUUID();

            // when/then
            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("BC-1003")));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid UUID format")
        void shouldReturn400ForInvalidUuid() throws Exception {
            // when/then
            mockMvc.perform(get("/v1/transfers/{transferId}", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("BC-0001")));
        }
    }

    @Nested
    @DisplayName("GET /v1/wallets/{walletId}/balance")
    class GetWalletBalance {

        @Test
        @DisplayName("should return 200 OK with wallet balance details")
        void shouldReturn200ForExistingWallet() throws Exception {
            // given
            var wallet = walletRepository.save(anActiveWallet());
            var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC)
                    .syncFromChain(new BigDecimal("5000.00"), 100L);
            walletBalanceRepository.save(balance);

            // when/then
            mockMvc.perform(get("/v1/wallets/{walletId}/balance", wallet.walletId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.walletId", is(wallet.walletId().toString())))
                    .andExpect(jsonPath("$.address", is(wallet.address())))
                    .andExpect(jsonPath("$.chainId", is("base")))
                    .andExpect(jsonPath("$.balances[0].stablecoin", is("USDC")))
                    .andExpect(jsonPath("$.balances[0].availableBalance", is("5000.00000000")));
        }

        @Test
        @DisplayName("should return 404 Not Found for non-existing wallet")
        void shouldReturn404ForNonExistingWallet() throws Exception {
            // given
            var walletId = UUID.randomUUID();

            // when/then
            mockMvc.perform(get("/v1/wallets/{walletId}/balance", walletId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("BC-1005")));
        }
    }
}
