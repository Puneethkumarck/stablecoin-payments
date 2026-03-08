package com.stablecoin.payments.custody.infrastructure.provider.solana;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.port.ChainRpcProvider;
import com.stablecoin.payments.custody.domain.port.TransactionReceipt;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.custody.solana.enabled", havingValue = "true")
@EnableConfigurationProperties(SolanaChainProperties.class)
public class SolanaRpcAdapter implements ChainRpcProvider {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final int USDC_DECIMALS = 6;
    private static final ChainId SOLANA_CHAIN = new ChainId("solana");

    private final RestClient restClient;
    private final SolanaChainProperties properties;

    public SolanaRpcAdapter(SolanaChainProperties properties) {
        this.properties = properties;

        var httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.rpcUrl())
                .requestFactory(requestFactory)
                .build();

        log.info("[SOLANA-RPC] Configured RestClient rpcUrl={} commitment={} usdcMint={}",
                properties.rpcUrl(), properties.commitment(), properties.usdcMintAddress());
    }

    @Override
    @CircuitBreaker(name = "solanaRpc", fallbackMethod = "getTransactionReceiptFallback")
    public TransactionReceipt getTransactionReceipt(ChainId chainId, String txHash) {
        log.info("[SOLANA-RPC] Getting transaction chain={} signature={}", chainId.value(), txHash);

        var commitmentConfig = Map.of(
                "encoding", "jsonParsed",
                "commitment", properties.commitment(),
                "maxSupportedTransactionVersion", 0
        );
        var result = callJsonRpc("getTransaction", txHash, commitmentConfig);
        if (result == null || result.isNull()) {
            log.info("[SOLANA-RPC] Transaction not found (pending) chain={} signature={}",
                    chainId.value(), txHash);
            return null;
        }

        var slot = result.get("slot").asLong();
        var meta = result.get("meta");
        var fee = meta != null && meta.has("fee") ? new BigDecimal(meta.get("fee").asLong()) : BigDecimal.ZERO;
        var success = meta == null || !meta.has("err") || meta.get("err").isNull();

        var latestSlot = getLatestBlockNumber(chainId);
        var confirmations = (int) (latestSlot - slot);

        log.info("[SOLANA-RPC] Transaction parsed chain={} signature={} slot={} success={} confirmations={}",
                chainId.value(), txHash, slot, success, confirmations);

        return new TransactionReceipt(txHash, slot, success, fee, BigDecimal.ZERO, confirmations);
    }

    @Override
    @CircuitBreaker(name = "solanaRpc", fallbackMethod = "getLatestBlockNumberFallback")
    public long getLatestBlockNumber(ChainId chainId) {
        log.info("[SOLANA-RPC] Getting current slot chain={}", chainId.value());

        var commitmentConfig = Map.of("commitment", properties.commitment());
        var result = callJsonRpc("getSlot", commitmentConfig);
        var slot = result.asLong();

        log.info("[SOLANA-RPC] Current slot chain={} slot={}", chainId.value(), slot);
        return slot;
    }

    @Override
    @CircuitBreaker(name = "solanaRpc", fallbackMethod = "getTokenBalanceFallback")
    public BigDecimal getTokenBalance(ChainId chainId, String address, String tokenContract) {
        log.info("[SOLANA-RPC] Getting SPL token balance chain={} owner={} mint={}",
                chainId.value(), address, tokenContract);

        var mintFilter = Map.of("mint", tokenContract);
        var encodingConfig = Map.of("encoding", "jsonParsed");
        var result = callJsonRpc("getTokenAccountsByOwner", address, mintFilter, encodingConfig);

        if (result == null || result.isNull()) {
            log.info("[SOLANA-RPC] No token accounts found chain={} owner={}", chainId.value(), address);
            return BigDecimal.ZERO.setScale(USDC_DECIMALS, RoundingMode.HALF_UP);
        }

        var accounts = result.get("value");
        if (accounts == null || !accounts.isArray() || accounts.isEmpty()) {
            log.info("[SOLANA-RPC] Empty token accounts chain={} owner={}", chainId.value(), address);
            return BigDecimal.ZERO.setScale(USDC_DECIMALS, RoundingMode.HALF_UP);
        }

        var totalBalance = BigDecimal.ZERO;
        for (var account : accounts) {
            var tokenAmount = account.get("account")
                    .get("data")
                    .get("parsed")
                    .get("info")
                    .get("tokenAmount");
            var uiAmountString = tokenAmount.get("uiAmountString").asText();
            totalBalance = totalBalance.add(new BigDecimal(uiAmountString));
        }

        var balance = totalBalance.setScale(USDC_DECIMALS, RoundingMode.HALF_UP);
        log.info("[SOLANA-RPC] SPL token balance chain={} owner={} balance={}",
                chainId.value(), address, balance);
        return balance;
    }

    JsonNode callJsonRpc(String method, Object... params) {
        var request = new JsonRpcRequest(method, params);

        var response = restClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);

        try {
            var responseNode = JSON_MAPPER.readTree(response);

            if (responseNode.has("error") && !responseNode.get("error").isNull()) {
                var error = responseNode.get("error");
                var code = error.has("code") ? error.get("code").asInt() : -1;
                var message = error.has("message") ? error.get("message").asText() : "Unknown RPC error";
                throw new SolanaRpcException(
                        "JSON-RPC error on %s: code=%d message=%s".formatted(method, code, message));
            }

            return responseNode.get("result");
        } catch (SolanaRpcException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SolanaRpcException("Failed to parse JSON-RPC response for %s".formatted(method), ex);
        }
    }

    @SuppressWarnings("unused")
    private TransactionReceipt getTransactionReceiptFallback(ChainId chainId, String txHash, Exception ex) {
        log.error("[SOLANA-RPC] Circuit breaker open - getTransactionReceipt failed chain={} signature={}",
                chainId.value(), txHash, ex);
        throw new IllegalStateException("Solana RPC unavailable for getTransactionReceipt", ex);
    }

    @SuppressWarnings("unused")
    private long getLatestBlockNumberFallback(ChainId chainId, Exception ex) {
        log.error("[SOLANA-RPC] Circuit breaker open - getLatestBlockNumber failed chain={}",
                chainId.value(), ex);
        throw new IllegalStateException("Solana RPC unavailable for getSlot", ex);
    }

    @SuppressWarnings("unused")
    private BigDecimal getTokenBalanceFallback(ChainId chainId, String address, String tokenContract,
                                               Exception ex) {
        log.error("[SOLANA-RPC] Circuit breaker open - getTokenBalance failed chain={} owner={}",
                chainId.value(), address, ex);
        throw new IllegalStateException("Solana RPC unavailable for getTokenBalance", ex);
    }

    record JsonRpcRequest(String jsonrpc, String method, Object[] params, int id) {

        JsonRpcRequest(String method, Object... params) {
            this("2.0", method, params, 1);
        }
    }
}
