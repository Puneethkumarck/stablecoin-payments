package com.stablecoin.payments.custody;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.port.ChainFeeProvider;
import com.stablecoin.payments.custody.domain.port.ChainHealthProvider;
import com.stablecoin.payments.custody.domain.port.ChainRpcProvider;
import com.stablecoin.payments.custody.domain.port.CustodyEngine;
import com.stablecoin.payments.custody.domain.port.SignRequest;
import com.stablecoin.payments.custody.domain.port.SignResult;
import com.stablecoin.payments.custody.domain.port.TransactionReceipt;
import com.stablecoin.payments.custody.domain.port.TransactionStatus;
import com.stablecoin.payments.custody.domain.service.BalanceSyncCommandHandler;
import com.stablecoin.payments.custody.domain.service.TransferMonitorCommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end business tests for the multi-chain transfer lifecycle.
 * <p>
 * Uses real Spring context with TestContainers (PostgreSQL + Kafka),
 * controllable {@link CustodyEngine}, {@link ChainRpcProvider},
 * {@link ChainHealthProvider}, and {@link ChainFeeProvider} that return
 * predictable, configurable results.
 * <p>
 * Each scenario exercises the full flow through REST endpoints using MockMvc,
 * then drives the monitor to simulate chain confirmations.
 */
@DisplayName("Transfer Lifecycle — Business Tests")
@ContextConfiguration(classes = TransferLifecycleTest.ControllableAdaptersConfig.class)
class TransferLifecycleTest extends AbstractBusinessTest {

    // ── Shared controllable state ────────────────────────────────────────

    private static final ConcurrentHashMap<String, TransactionReceipt> RECEIPT_MAP =
            new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, AtomicLong> BLOCK_NUMBER_MAP =
            new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Double> HEALTH_SCORE_MAP =
            new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Double> FEE_MAP =
            new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, BigDecimal> TOKEN_BALANCE_MAP =
            new ConcurrentHashMap<>();

    private static final AtomicLong TX_COUNTER = new AtomicLong(0);

    @TestConfiguration
    static class ControllableAdaptersConfig {

        @Bean
        @Primary
        public CustodyEngine controllableCustodyEngine() {
            return new CustodyEngine() {
                @Override
                public SignResult signAndSubmit(SignRequest request) {
                    var counter = TX_COUNTER.incrementAndGet();
                    var txHash = "0x" + String.format("%064x", counter);
                    return new SignResult(txHash, "custody-tx-" + counter);
                }

                @Override
                public TransactionStatus getTransactionStatus(String txId) {
                    return new TransactionStatus("COMPLETED", txId, 10);
                }
            };
        }

        @Bean
        @Primary
        public ChainRpcProvider controllableChainRpcProvider() {
            return new ChainRpcProvider() {
                @Override
                public TransactionReceipt getTransactionReceipt(ChainId chainId, String txHash) {
                    return RECEIPT_MAP.get(chainId.value() + ":" + txHash);
                }

                @Override
                public long getLatestBlockNumber(ChainId chainId) {
                    var counter = BLOCK_NUMBER_MAP.get(chainId.value());
                    return counter != null ? counter.get() : 1000L;
                }

                @Override
                public BigDecimal getTokenBalance(ChainId chainId, String address, String tokenContract) {
                    return TOKEN_BALANCE_MAP.getOrDefault(
                            chainId.value() + ":" + address, new BigDecimal("500000"));
                }
            };
        }

        @Bean
        @Primary
        public ChainHealthProvider controllableChainHealthProvider() {
            return chainId -> HEALTH_SCORE_MAP.getOrDefault(chainId.value(), 1.0);
        }

        @Bean
        @Primary
        public ChainFeeProvider controllableChainFeeProvider() {
            return (chainId, stablecoin) -> FEE_MAP.getOrDefault(chainId.value(), defaultFee(chainId));
        }

        private static double defaultFee(ChainId chainId) {
            return switch (chainId.value()) {
                case "base" -> 0.01;
                case "ethereum" -> 2.50;
                case "solana" -> 0.005;
                default -> 1.0;
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransferMonitorCommandHandler transferMonitorCommandHandler;

    @Autowired
    private BalanceSyncCommandHandler balanceSyncCommandHandler;

    @BeforeEach
    void resetControllableState() {
        RECEIPT_MAP.clear();
        BLOCK_NUMBER_MAP.clear();
        HEALTH_SCORE_MAP.clear();
        FEE_MAP.clear();
        TOKEN_BALANCE_MAP.clear();
        TX_COUNTER.set(0);

        HEALTH_SCORE_MAP.put("base", 1.0);
        HEALTH_SCORE_MAP.put("ethereum", 1.0);
        HEALTH_SCORE_MAP.put("solana", 1.0);

        BLOCK_NUMBER_MAP.put("base", new AtomicLong(1000L));
        BLOCK_NUMBER_MAP.put("ethereum", new AtomicLong(1000L));
        BLOCK_NUMBER_MAP.put("solana", new AtomicLong(1000L));

        // Re-seed wallets after each cleanDatabase() (which TRUNCATES all tables)
        seedAllWallets();
    }

    // ── Wallet Seeding ───────────────────────────────────────────────────

    private void seedAllWallets() {
        // Base ON_RAMP wallet
        var baseOnRampId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO wallets (wallet_id, chain_id, address, address_checksum, tier, purpose, custodian, vault_account_id, stablecoin, is_active)
                VALUES (?, 'base', '0x1111222233334444555566667777888899990000', '0x1111222233334444555566667777888899990000', 'HOT', 'ON_RAMP', 'dev', 'dev-vault-onramp', 'USDC', true)
                """, baseOnRampId);
        insertBalanceAndNonce(baseOnRampId, "base");

        // Base OFF_RAMP wallet (for RETURN transfers)
        var baseOffRampId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO wallets (wallet_id, chain_id, address, address_checksum, tier, purpose, custodian, vault_account_id, stablecoin, is_active)
                VALUES (?, 'base', '0xaaaa222233334444555566667777888899990000', '0xaaaa222233334444555566667777888899990000', 'HOT', 'OFF_RAMP', 'dev', 'dev-vault-offramp', 'USDC', true)
                """, baseOffRampId);
        insertBalanceAndNonce(baseOffRampId, "base");

        // Ethereum ON_RAMP wallet
        var ethOnRampId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO wallets (wallet_id, chain_id, address, address_checksum, tier, purpose, custodian, vault_account_id, stablecoin, is_active)
                VALUES (?, 'ethereum', '0xeth1111222233334444555566667777888899990000', '0xeth1111222233334444555566667777888899990000', 'HOT', 'ON_RAMP', 'dev', 'dev-vault-eth-onramp', 'USDC', true)
                """, ethOnRampId);
        insertBalanceAndNonce(ethOnRampId, "ethereum");

        // Solana ON_RAMP wallet
        var solOnRampId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO wallets (wallet_id, chain_id, address, address_checksum, tier, purpose, custodian, vault_account_id, stablecoin, is_active)
                VALUES (?, 'solana', 'SolWallet1111222233334444555566667777888899990000', 'SolWallet1111222233334444555566667777888899990000', 'HOT', 'ON_RAMP', 'dev', 'dev-vault-sol-onramp', 'USDC', true)
                """, solOnRampId);
        insertBalanceAndNonce(solOnRampId, "solana");
    }

    private void insertBalanceAndNonce(UUID walletId, String chainId) {
        jdbcTemplate.update(
                """
                INSERT INTO wallet_balances (balance_id, wallet_id, chain_id, stablecoin, available_balance, reserved_balance, blockchain_balance, last_indexed_block)
                VALUES (?, ?, ?, 'USDC', 500000.00000000, 0.00000000, 500000.00000000, 0)
                """, UUID.randomUUID(), walletId, chainId);
        jdbcTemplate.update(
                "INSERT INTO wallet_nonces (wallet_id, chain_id, current_nonce) VALUES (?, ?, 0)",
                walletId, chainId);
    }

    // ── JSON Helpers ─────────────────────────────────────────────────────

    private String forwardTransferJson(UUID paymentId, UUID correlationId, String preferredChain) {
        if (preferredChain != null) {
            return """
                    {
                      "paymentId": "%s",
                      "correlationId": "%s",
                      "transferType": "FORWARD",
                      "stablecoin": "USDC",
                      "amount": "1000.00",
                      "toWalletAddress": "0xRecipientAddress1234567890abcdef",
                      "preferredChain": "%s"
                    }
                    """.formatted(paymentId, correlationId, preferredChain);
        }
        return """
                {
                  "paymentId": "%s",
                  "correlationId": "%s",
                  "transferType": "FORWARD",
                  "stablecoin": "USDC",
                  "amount": "1000.00",
                  "toWalletAddress": "0xRecipientAddress1234567890abcdef"
                }
                """.formatted(paymentId, correlationId);
    }

    private String returnTransferJson(UUID paymentId, UUID correlationId,
                                       UUID parentTransferId, String preferredChain) {
        return """
                {
                  "paymentId": "%s",
                  "correlationId": "%s",
                  "transferType": "RETURN",
                  "parentTransferId": "%s",
                  "stablecoin": "USDC",
                  "amount": "1000.00",
                  "toWalletAddress": "0xRecipientAddress1234567890abcdef",
                  "preferredChain": "%s"
                }
                """.formatted(paymentId, correlationId, parentTransferId, preferredChain);
    }

    private static String extractField(String jsonResponse, String fieldName) {
        var pattern = "\"" + fieldName + "\":\"";
        var start = jsonResponse.indexOf(pattern) + pattern.length();
        var end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }

    private String createForwardTransfer(UUID paymentId, UUID correlationId,
                                         String preferredChain) throws Exception {
        var result = mockMvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forwardTransferJson(paymentId, correlationId, preferredChain)))
                .andExpect(status().isAccepted())
                .andReturn();
        return extractField(result.getResponse().getContentAsString(), "transferId");
    }

    private String getTxHash(String transferId) throws Exception {
        var result = mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                .andExpect(status().isOk())
                .andReturn();
        return extractField(result.getResponse().getContentAsString(), "txHash");
    }

    private void configureConfirmedReceipt(String chainId, String txHash, long blockNumber) {
        RECEIPT_MAP.put(chainId + ":" + txHash, new TransactionReceipt(
                txHash, blockNumber, true,
                new BigDecimal("21000"), new BigDecimal("25"), 0));
    }

    private void setLatestBlock(String chainId, long blockNumber) {
        BLOCK_NUMBER_MAP.computeIfAbsent(chainId, k -> new AtomicLong(0)).set(blockNumber);
    }

    private void assertOutboxContainsEvents(UUID paymentId, String... expectedEventTypes) {
        var outboxTypes = jdbcTemplate.queryForList(
                "SELECT record_type FROM custody_outbox_record WHERE record_key = ? ORDER BY id",
                String.class, paymentId.toString());
        assertThat(outboxTypes).hasSize(expectedEventTypes.length);
        for (var expectedType : expectedEventTypes) {
            assertThat(outboxTypes).anyMatch(type -> type.contains(expectedType));
        }
    }

    // ── Scenario 1: Happy Path Base ─────────────────────────────────────

    @Nested
    @DisplayName("1. Happy Path Base — submit -> SUBMITTED -> CONFIRMING -> CONFIRMED")
    class HappyPathBase {

        @Test
        @DisplayName("should complete full Base transfer lifecycle with 1 confirmation")
        void shouldCompleteBaseTransferLifecycle() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. POST /v1/transfers -> 202
            var transferId = createForwardTransfer(paymentId, correlationId, "base");

            // 2. GET -> verify SUBMITTED on Base
            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transferId", is(transferId)))
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("SUBMITTED")))
                    .andExpect(jsonPath("$.chainId", is("base")))
                    .andExpect(jsonPath("$.stablecoin", is("USDC")))
                    .andExpect(jsonPath("$.amount", is("1000.00000000")))
                    .andExpect(jsonPath("$.txHash", notNullValue()))
                    .andExpect(jsonPath("$.createdAt", notNullValue()));

            // 3. Configure receipt at block 100, latest block 101 (1 confirmation >= 1 for Base)
            var txHash = getTxHash(transferId);
            configureConfirmedReceipt("base", txHash, 100L);
            setLatestBlock("base", 101L);

            // 4. Run monitor -> CONFIRMED
            transferMonitorCommandHandler.monitorPendingTransfers();

            // 5. GET -> verify CONFIRMED
            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CONFIRMED")))
                    .andExpect(jsonPath("$.blockNumber", is("100")))
                    .andExpect(jsonPath("$.confirmedAt", notNullValue()));

            // 6. Verify outbox events (submitted + confirmed)
            assertOutboxContainsEvents(paymentId,
                    "TransferSubmittedEvent", "TransferConfirmedEvent");
        }
    }

    // ── Scenario 2: Happy Path Ethereum ─────────────────────────────────

    @Nested
    @DisplayName("2. Happy Path Ethereum — 32 confirmations required")
    class HappyPathEthereum {

        @Test
        @DisplayName("should confirm Ethereum transfer after 32 confirmations")
        void shouldConfirmEthereumTransferAfter32Confirmations() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Submit with preferred_chain=ethereum
            var transferId = createForwardTransfer(paymentId, correlationId, "ethereum");

            // 2. Verify SUBMITTED on ethereum
            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("SUBMITTED")))
                    .andExpect(jsonPath("$.chainId", is("ethereum")));

            // 3. Receipt at block 500; latest = 520 (only 20 confirmations, need 32)
            var txHash = getTxHash(transferId);
            configureConfirmedReceipt("ethereum", txHash, 500L);
            setLatestBlock("ethereum", 520L);

            // 4. Monitor -> CONFIRMING (not yet CONFIRMED)
            transferMonitorCommandHandler.monitorPendingTransfers();

            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CONFIRMING")));

            // 5. Advance to block 532 (32 confirmations)
            setLatestBlock("ethereum", 532L);

            // 6. Monitor -> CONFIRMED
            transferMonitorCommandHandler.monitorPendingTransfers();

            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CONFIRMED")))
                    .andExpect(jsonPath("$.blockNumber", is("500")));

            // 7. Verify outbox events (submitted + confirmed)
            assertOutboxContainsEvents(paymentId,
                    "TransferSubmittedEvent", "TransferConfirmedEvent");
        }
    }

    // ── Scenario 3: Happy Path Solana ───────────────────────────────────

    @Nested
    @DisplayName("3. Happy Path Solana — SPL transfer confirmed")
    class HappyPathSolana {

        @Test
        @DisplayName("should confirm Solana transfer with 1 confirmation")
        void shouldConfirmSolanaTransferWith1Confirmation() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Submit with preferred_chain=solana
            var transferId = createForwardTransfer(paymentId, correlationId, "solana");

            // 2. Verify SUBMITTED on solana
            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("SUBMITTED")))
                    .andExpect(jsonPath("$.chainId", is("solana")));

            // 3. Receipt at block 800; latest = 801 (1 confirmation for Solana)
            var txHash = getTxHash(transferId);
            configureConfirmedReceipt("solana", txHash, 800L);
            setLatestBlock("solana", 801L);

            // 4. Monitor -> CONFIRMED
            transferMonitorCommandHandler.monitorPendingTransfers();

            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CONFIRMED")))
                    .andExpect(jsonPath("$.blockNumber", is("800")));

            // 5. Verify outbox events (submitted + confirmed)
            assertOutboxContainsEvents(paymentId,
                    "TransferSubmittedEvent", "TransferConfirmedEvent");
        }
    }

    // ── Scenario 4: Chain Selection Fallback ────────────────────────────

    @Nested
    @DisplayName("4. Chain Selection — unhealthy Base -> fallback to Ethereum")
    class ChainSelectionFallback {

        @Test
        @DisplayName("should fallback to Ethereum when Base is unhealthy")
        void shouldFallbackToEthereumWhenBaseUnhealthy() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Mark Base as unhealthy
            HEALTH_SCORE_MAP.put("base", 0.0);

            // Submit without preferred chain
            var result = mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(forwardTransferJson(paymentId, correlationId, null)))
                    .andExpect(status().isAccepted())
                    .andReturn();

            var transferId = extractField(result.getResponse().getContentAsString(), "transferId");
            var chainId = extractField(result.getResponse().getContentAsString(), "chainId");

            // Should NOT be Base
            assertThat(chainId).isNotEqualTo("base");
            assertThat(chainId).isIn("ethereum", "solana");

            // Confirm SUBMITTED
            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("SUBMITTED")));
        }
    }

    // ── Scenario 5: Return (Compensation) Transfer ─────────────────────

    @Nested
    @DisplayName("5. Return — CONFIRMED forward -> submit return -> return confirmed")
    class ReturnCompensation {

        @Test
        @DisplayName("should complete return transfer after forward is confirmed")
        void shouldCompleteReturnTransferAfterForwardConfirmed() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Submit and confirm a FORWARD transfer
            var forwardTransferId = createForwardTransfer(paymentId, correlationId, "base");
            var forwardTxHash = getTxHash(forwardTransferId);
            configureConfirmedReceipt("base", forwardTxHash, 100L);
            setLatestBlock("base", 101L);
            transferMonitorCommandHandler.monitorPendingTransfers();

            mockMvc.perform(get("/v1/transfers/{transferId}", forwardTransferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CONFIRMED")));

            // 2. Submit RETURN transfer (different paymentId, unique per payment_id+transfer_type)
            var returnPaymentId = UUID.randomUUID();
            var returnCorrelationId = UUID.randomUUID();

            var returnResult = mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(returnTransferJson(returnPaymentId, returnCorrelationId,
                                    UUID.fromString(forwardTransferId), "base")))
                    .andExpect(status().isAccepted())
                    .andReturn();

            var returnTransferId = extractField(returnResult.getResponse().getContentAsString(), "transferId");

            // 3. Verify RETURN is SUBMITTED
            mockMvc.perform(get("/v1/transfers/{transferId}", returnTransferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("SUBMITTED")));

            // 4. Confirm the RETURN transfer
            var returnTxHash = getTxHash(returnTransferId);
            configureConfirmedReceipt("base", returnTxHash, 200L);
            setLatestBlock("base", 201L);
            transferMonitorCommandHandler.monitorPendingTransfers();

            // 5. Verify RETURN is CONFIRMED
            mockMvc.perform(get("/v1/transfers/{transferId}", returnTransferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CONFIRMED")));

            // 6. Verify outbox events for return (submitted + confirmed)
            assertOutboxContainsEvents(returnPaymentId,
                    "TransferSubmittedEvent", "TransferConfirmedEvent");
        }
    }

    // ── Scenario 6: Insufficient Balance ────────────────────────────────

    @Nested
    @DisplayName("6. Insufficient Balance — reject with 503")
    class InsufficientBalance {

        @Test
        @DisplayName("should reject transfer when all chains have insufficient balance")
        void shouldRejectTransferWhenBalanceInsufficient() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Drain all wallet balances
            jdbcTemplate.update(
                    "UPDATE wallet_balances SET available_balance = 0.00000001, blockchain_balance = 0.00000001");

            // Submit -> 503 (no chain has sufficient balance, ChainUnavailableException)
            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(forwardTransferJson(paymentId, correlationId, null)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code", is("BC-1002")));
        }
    }

    // ── Scenario 7: Idempotency ─────────────────────────────────────────

    @Nested
    @DisplayName("7. Idempotency — same paymentId -> 200 OK")
    class Idempotency {

        @Test
        @DisplayName("should return 200 OK for duplicate paymentId with same transferId")
        void shouldReturn200ForDuplicatePaymentId() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();
            var requestBody = forwardTransferJson(paymentId, correlationId, "base");

            // 1. First request -> 202 ACCEPTED
            var firstResult = mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isAccepted())
                    .andReturn();

            var firstTransferId = extractField(
                    firstResult.getResponse().getContentAsString(), "transferId");

            // 2. Second request with same paymentId -> 200 OK
            var secondResult = mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andReturn();

            var secondTransferId = extractField(
                    secondResult.getResponse().getContentAsString(), "transferId");

            // 3. Same transferId
            assertThat(secondTransferId).isEqualTo(firstTransferId);
        }
    }

    // ── Scenario 8: Stuck Resubmission ──────────────────────────────────

    @Nested
    @DisplayName("8. Stuck Resubmission — SUBMITTED > 120s -> resubmit -> CONFIRMED")
    class StuckResubmission {

        @Test
        @DisplayName("should resubmit stuck transfer and eventually confirm")
        void shouldResubmitStuckTransferAndConfirm() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Submit -> SUBMITTED
            var transferId = createForwardTransfer(paymentId, correlationId, "base");

            // Capture original txHash before making it stuck
            var originalTxHash = getTxHash(transferId);

            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("SUBMITTED")));

            // 2. Make transfer appear stuck (updated_at > 120s ago)
            jdbcTemplate.update(
                    "UPDATE chain_transfers SET updated_at = ? WHERE transfer_id = ?::uuid",
                    Timestamp.from(Instant.now().minusSeconds(300)),
                    transferId);

            // 3. Monitor #1: detects stuck SUBMITTED -> transitions to RESUBMITTING
            transferMonitorCommandHandler.monitorPendingTransfers();

            // 4. Monitor #2: processes RESUBMITTING -> re-signs -> back to SUBMITTED (new txHash)
            transferMonitorCommandHandler.monitorPendingTransfers();

            // After resubmission: transfer is SUBMITTED with a new txHash
            var newTxHash = getTxHash(transferId);
            assertThat(newTxHash).isNotEqualTo(originalTxHash);

            // 5. Configure receipt for new txHash
            configureConfirmedReceipt("base", newTxHash, 300L);
            setLatestBlock("base", 301L);

            // 6. Monitor #3: processes SUBMITTED -> finds receipt -> CONFIRMED
            transferMonitorCommandHandler.monitorPendingTransfers();

            mockMvc.perform(get("/v1/transfers/{transferId}", transferId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CONFIRMED")));

            // 6. Verify attempt_count > 1
            var attemptCount = jdbcTemplate.queryForObject(
                    "SELECT attempt_count FROM chain_transfers WHERE transfer_id = ?::uuid",
                    Integer.class, transferId);
            assertThat(attemptCount).isGreaterThan(1);
        }
    }

    // ── Scenario 9: Balance Sync ────────────────────────────────────────

    @Nested
    @DisplayName("9. Balance Sync — blockchain_balance updated after confirmation")
    class BalanceSync {

        @Test
        @DisplayName("should update blockchain_balance after confirmation and sync")
        void shouldUpdateBlockchainBalanceAfterConfirmationAndSync() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // 1. Get the Base ON_RAMP wallet
            var baseWalletId = jdbcTemplate.queryForObject(
                    "SELECT wallet_id FROM wallets WHERE chain_id = 'base' AND purpose = 'ON_RAMP' LIMIT 1",
                    UUID.class);
            var walletAddress = jdbcTemplate.queryForObject(
                    "SELECT address FROM wallets WHERE wallet_id = ?",
                    String.class, baseWalletId);

            // 2. Record initial available balance
            var initialAvailable = jdbcTemplate.queryForObject(
                    "SELECT available_balance FROM wallet_balances WHERE wallet_id = ? AND stablecoin = 'USDC'",
                    BigDecimal.class, baseWalletId);

            // 3. Submit and confirm a transfer
            var transferId = createForwardTransfer(paymentId, correlationId, "base");
            var txHash = getTxHash(transferId);
            configureConfirmedReceipt("base", txHash, 100L);
            setLatestBlock("base", 101L);
            transferMonitorCommandHandler.monitorPendingTransfers();

            // 4. After confirmation, reserved should be debited (back to 0)
            var postConfirmReserved = jdbcTemplate.queryForObject(
                    "SELECT reserved_balance FROM wallet_balances WHERE wallet_id = ? AND stablecoin = 'USDC'",
                    BigDecimal.class, baseWalletId);
            assertThat(postConfirmReserved.compareTo(BigDecimal.ZERO)).isEqualTo(0);

            // 5. Configure on-chain balance as reduced after transfer
            var expectedOnChainBalance = initialAvailable.subtract(new BigDecimal("1000.00000000"));
            TOKEN_BALANCE_MAP.put("base:" + walletAddress, expectedOnChainBalance);
            setLatestBlock("base", 102L);

            // 6. Run balance sync
            balanceSyncCommandHandler.syncAllBalances();

            // 7. Verify blockchain_balance updated
            var syncedBlockchainBalance = jdbcTemplate.queryForObject(
                    "SELECT blockchain_balance FROM wallet_balances WHERE wallet_id = ? AND stablecoin = 'USDC'",
                    BigDecimal.class, baseWalletId);
            assertThat(syncedBlockchainBalance.compareTo(expectedOnChainBalance)).isEqualTo(0);
        }
    }

    // ── Scenario 10: All Chains Drained ─────────────────────────────────

    @Nested
    @DisplayName("10. All Chains Drained — 503 Service Unavailable")
    class AllChainsDrained {

        @Test
        @DisplayName("should return 503 when no chain has sufficient balance")
        void shouldReturn503WhenAllChainsDrained() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Drain ALL wallets
            jdbcTemplate.update(
                    "UPDATE wallet_balances SET available_balance = 0.00000001, blockchain_balance = 0.00000001");

            // Submit with preferred_chain=base -> 503 (chain unavailable)
            mockMvc.perform(post("/v1/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(forwardTransferJson(paymentId, correlationId, "base")))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code", is("BC-1002")));
        }
    }
}
