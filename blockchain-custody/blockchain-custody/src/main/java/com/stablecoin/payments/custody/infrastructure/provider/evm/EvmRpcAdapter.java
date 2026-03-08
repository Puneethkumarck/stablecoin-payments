package com.stablecoin.payments.custody.infrastructure.provider.evm;

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
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.custody.evm.enabled", havingValue = "true")
@EnableConfigurationProperties(EvmChainProperties.class)
public class EvmRpcAdapter implements ChainRpcProvider {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final int USDC_DECIMALS = 6;
    private static final String BALANCE_OF_SELECTOR = "0x70a08231";

    private final Map<String, RestClient> restClients;
    private final EvmChainProperties properties;

    public EvmRpcAdapter(EvmChainProperties properties) {
        this.properties = properties;
        this.restClients = new ConcurrentHashMap<>();

        properties.chains().forEach((chainName, config) -> {
            var httpClient = HttpClient.newBuilder()
                    .version(Version.HTTP_1_1)
                    .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                    .build();

            var requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(Duration.ofMillis(config.readTimeoutMs()));

            var client = RestClient.builder()
                    .baseUrl(config.rpcUrl())
                    .requestFactory(requestFactory)
                    .build();

            restClients.put(chainName, client);
            log.info("[EVM-RPC] Configured RestClient for chain={} rpcUrl={} chainId={}",
                    chainName, config.rpcUrl(), config.chainId());
        });
    }

    @Override
    @CircuitBreaker(name = "evmRpc", fallbackMethod = "getTransactionReceiptFallback")
    public TransactionReceipt getTransactionReceipt(ChainId chainId, String txHash) {
        log.info("[EVM-RPC] Getting transaction receipt chain={} txHash={}", chainId.value(), txHash);

        var receiptResult = callJsonRpc(chainId, "eth_getTransactionReceipt", txHash);
        if (receiptResult == null || receiptResult.isNull()) {
            log.info("[EVM-RPC] Transaction receipt not found (pending) chain={} txHash={}",
                    chainId.value(), txHash);
            return null;
        }

        var blockNumber = hexToLong(receiptResult.get("blockNumber").asText());
        var status = receiptResult.get("status").asText();
        var gasUsed = new BigDecimal(hexToBigInteger(receiptResult.get("gasUsed").asText()));
        var effectiveGasPrice = new BigDecimal(hexToBigInteger(receiptResult.get("effectiveGasPrice").asText()));
        var success = "0x1".equals(status);

        var latestBlock = getLatestBlockNumber(chainId);
        var confirmations = (int) (latestBlock - blockNumber);

        log.info("[EVM-RPC] Receipt parsed chain={} txHash={} block={} success={} confirmations={}",
                chainId.value(), txHash, blockNumber, success, confirmations);

        return new TransactionReceipt(txHash, blockNumber, success, gasUsed, effectiveGasPrice, confirmations);
    }

    @Override
    @CircuitBreaker(name = "evmRpc", fallbackMethod = "getLatestBlockNumberFallback")
    public long getLatestBlockNumber(ChainId chainId) {
        log.info("[EVM-RPC] Getting latest block number chain={}", chainId.value());

        var result = callJsonRpc(chainId, "eth_blockNumber");
        var blockNumber = hexToLong(result.asText());

        log.info("[EVM-RPC] Latest block chain={} blockNumber={}", chainId.value(), blockNumber);
        return blockNumber;
    }

    @Override
    @CircuitBreaker(name = "evmRpc", fallbackMethod = "getTokenBalanceFallback")
    public BigDecimal getTokenBalance(ChainId chainId, String address, String tokenContract) {
        log.info("[EVM-RPC] Getting token balance chain={} address={} contract={}",
                chainId.value(), address, tokenContract);

        var callData = encodeBalanceOfCall(address);
        var callParam = Map.of("to", tokenContract, "data", callData);
        var result = callJsonRpc(chainId, "eth_call", callParam, "latest");

        var rawBalance = hexToBigInteger(result.asText());
        var balance = new BigDecimal(rawBalance).divide(
                BigDecimal.TEN.pow(USDC_DECIMALS), USDC_DECIMALS, RoundingMode.HALF_UP);

        log.info("[EVM-RPC] Token balance chain={} address={} balance={}",
                chainId.value(), address, balance);
        return balance;
    }

    JsonNode callJsonRpc(ChainId chainId, String method, Object... params) {
        var client = restClients.get(chainId.value());
        if (client == null) {
            throw new IllegalArgumentException(
                    "No RPC client configured for chain: %s".formatted(chainId.value()));
        }

        var request = new JsonRpcRequest(method, params);

        var response = client.post()
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
                throw new EvmRpcException(
                        "JSON-RPC error on %s: code=%d message=%s".formatted(method, code, message));
            }

            return responseNode.get("result");
        } catch (EvmRpcException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EvmRpcException("Failed to parse JSON-RPC response for %s".formatted(method), ex);
        }
    }

    static long hexToLong(String hex) {
        if (hex == null || hex.isBlank()) {
            throw new EvmRpcException("Cannot convert null/blank hex string to long");
        }
        var stripped = hex.startsWith("0x") ? hex.substring(2) : hex;
        return Long.parseLong(stripped, 16);
    }

    static BigInteger hexToBigInteger(String hex) {
        if (hex == null || hex.isBlank()) {
            throw new EvmRpcException("Cannot convert null/blank hex string to BigInteger");
        }
        var stripped = hex.startsWith("0x") ? hex.substring(2) : hex;
        return new BigInteger(stripped, 16);
    }

    static String encodeBalanceOfCall(String address) {
        var cleanAddress = address.startsWith("0x") ? address.substring(2) : address;
        var paddedAddress = "0".repeat(64 - cleanAddress.length()) + cleanAddress.toLowerCase();
        return BALANCE_OF_SELECTOR + paddedAddress;
    }

    @SuppressWarnings("unused")
    private TransactionReceipt getTransactionReceiptFallback(ChainId chainId, String txHash, Exception ex) {
        log.error("[EVM-RPC] Circuit breaker open — getTransactionReceipt failed chain={} txHash={}",
                chainId.value(), txHash, ex);
        throw new IllegalStateException("EVM RPC unavailable for getTransactionReceipt", ex);
    }

    @SuppressWarnings("unused")
    private long getLatestBlockNumberFallback(ChainId chainId, Exception ex) {
        log.error("[EVM-RPC] Circuit breaker open — getLatestBlockNumber failed chain={}",
                chainId.value(), ex);
        throw new IllegalStateException("EVM RPC unavailable for getLatestBlockNumber", ex);
    }

    @SuppressWarnings("unused")
    private BigDecimal getTokenBalanceFallback(ChainId chainId, String address, String tokenContract,
                                               Exception ex) {
        log.error("[EVM-RPC] Circuit breaker open — getTokenBalance failed chain={} address={}",
                chainId.value(), address, ex);
        throw new IllegalStateException("EVM RPC unavailable for getTokenBalance", ex);
    }

    record JsonRpcRequest(String jsonrpc, String method, Object[] params, int id) {

        JsonRpcRequest(String method, Object... params) {
            this("2.0", method, params, 1);
        }
    }

    record JsonRpcResponse(String jsonrpc, int id, JsonNode result, JsonRpcError error) {

        record JsonRpcError(int code, String message) {}
    }
}
