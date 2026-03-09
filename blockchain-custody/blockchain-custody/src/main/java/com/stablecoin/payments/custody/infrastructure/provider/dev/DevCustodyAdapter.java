package com.stablecoin.payments.custody.infrastructure.provider.dev;

import com.stablecoin.payments.custody.domain.port.CustodyEngine;
import com.stablecoin.payments.custody.domain.port.SignRequest;
import com.stablecoin.payments.custody.domain.port.SignResult;
import com.stablecoin.payments.custody.domain.port.TransactionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.custody.provider", havingValue = "dev")
@EnableConfigurationProperties(DevCustodyProperties.class)
public class DevCustodyAdapter implements CustodyEngine {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String TRANSFER_SELECTOR = "a9059cbb";
    private static final int USDC_DECIMALS = 6;

    private final DevCustodyProperties properties;
    private final Map<String, RestClient> restClients;
    private final Map<String, TxMapping> txMappings;

    public DevCustodyAdapter(DevCustodyProperties properties) {
        this.properties = properties;
        this.restClients = new ConcurrentHashMap<>();
        this.txMappings = new ConcurrentHashMap<>();

        initRestClients();
    }

    DevCustodyAdapter(DevCustodyProperties properties, Map<String, RestClient> restClients) {
        this.properties = properties;
        this.restClients = new ConcurrentHashMap<>(restClients);
        this.txMappings = new ConcurrentHashMap<>();
    }

    @Override
    public SignResult signAndSubmit(SignRequest request) {
        log.info("[DEV-CUSTODY] Signing and submitting transfer transferId={} chain={} to={}",
                request.transferId(), request.chainId().value(), request.toAddress());

        return switch (request.chainId().value()) {
            case "base", "ethereum" -> signAndSubmitEvm(request);
            case "solana" -> signAndSubmitSolana(request);
            default -> throw new IllegalArgumentException(
                    "Unsupported chain for dev custody: %s".formatted(request.chainId().value()));
        };
    }

    @Override
    public TransactionStatus getTransactionStatus(String txId) {
        log.info("[DEV-CUSTODY] Getting transaction status txId={}", txId);

        var mapping = txMappings.get(txId);
        if (mapping == null) {
            log.warn("[DEV-CUSTODY] No tx mapping found for txId={}, returning PENDING", txId);
            return new TransactionStatus("PENDING", null, 0);
        }

        return new TransactionStatus("SUBMITTED", mapping.txHash(), 0);
    }

    SignResult signAndSubmitEvm(SignRequest request) {
        var chainConfig = resolveEvmChainConfig(request.chainId().value());
        var credentials = Credentials.create(properties.evmPrivateKey());

        var usdcContract = chainConfig.usdcContract();
        var data = encodeErc20Transfer(request.toAddress(), request.amount().toBigInteger());

        var nonce = request.nonce() != null ? BigInteger.valueOf(request.nonce()) : BigInteger.ZERO;

        var rawTransaction = RawTransaction.createTransaction(
                nonce,
                BigInteger.valueOf(properties.gasPrice()),
                BigInteger.valueOf(properties.gasLimit()),
                usdcContract,
                BigInteger.ZERO,
                data
        );

        var signedMessage = TransactionEncoder.signMessage(rawTransaction, chainConfig.chainId(), credentials);
        var signedTxHex = Numeric.toHexString(signedMessage);

        log.info("[DEV-CUSTODY] Signed EVM transaction chain={} nonce={} gasPrice={} gasLimit={}",
                request.chainId().value(), nonce, properties.gasPrice(), properties.gasLimit());

        var txHash = sendRawTransaction(request.chainId().value(), signedTxHex);
        var custodyTxId = "dev-" + UUID.randomUUID();

        txMappings.put(custodyTxId, new TxMapping(txHash, request.chainId().value()));

        log.info("[DEV-CUSTODY] EVM transaction submitted chain={} txHash={} custodyTxId={}",
                request.chainId().value(), txHash, custodyTxId);

        return new SignResult(txHash, custodyTxId);
    }

    SignResult signAndSubmitSolana(SignRequest request) {
        log.warn("[DEV-CUSTODY] Solana signing is SIMULATED — real signing requires solanaj. " +
                "transferId={} to={} amount={}",
                request.transferId(), request.toAddress(), request.amount());

        var simulatedSignature = "sim-" + UUID.nameUUIDFromBytes(
                (request.transferId().toString() + request.toAddress()).getBytes()
        ).toString().replace("-", "");

        var custodyTxId = "dev-" + UUID.randomUUID();
        txMappings.put(custodyTxId, new TxMapping(simulatedSignature, "solana"));

        log.info("[DEV-CUSTODY] Solana simulated signature={} custodyTxId={}",
                simulatedSignature, custodyTxId);

        return new SignResult(simulatedSignature, custodyTxId);
    }

    EvmChainConfig resolveEvmChainConfig(String chain) {
        return switch (chain) {
            case "base" -> new EvmChainConfig(
                    properties.baseChainId(),
                    properties.baseRpcUrl(),
                    properties.baseUsdcContract()
            );
            case "ethereum" -> new EvmChainConfig(
                    properties.ethereumChainId(),
                    properties.ethereumRpcUrl(),
                    properties.ethereumUsdcContract()
            );
            default -> throw new IllegalArgumentException(
                    "Unsupported EVM chain: %s".formatted(chain));
        };
    }

    static String encodeErc20Transfer(String toAddress, BigInteger amount) {
        var cleanAddress = toAddress.startsWith("0x") ? toAddress.substring(2) : toAddress;
        var paddedAddress = "0".repeat(64 - cleanAddress.length()) + cleanAddress.toLowerCase();

        var amountScaled = amount.multiply(BigInteger.TEN.pow(USDC_DECIMALS));
        var amountHex = amountScaled.toString(16);
        var paddedAmount = "0".repeat(64 - amountHex.length()) + amountHex;

        return "0x" + TRANSFER_SELECTOR + paddedAddress + paddedAmount;
    }

    private String sendRawTransaction(String chain, String signedTxHex) {
        var client = restClients.get(chain);
        if (client == null) {
            throw new IllegalStateException(
                    "No RPC client configured for chain: %s".formatted(chain));
        }

        var request = new JsonRpcRequest("eth_sendRawTransaction", signedTxHex);

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
                throw new DevCustodyException(
                        "eth_sendRawTransaction failed: code=%d message=%s".formatted(code, message));
            }

            return responseNode.get("result").asText();
        } catch (DevCustodyException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DevCustodyException("Failed to parse eth_sendRawTransaction response", ex);
        }
    }

    private void initRestClients() {
        if (properties.baseRpcUrl() != null && !properties.baseRpcUrl().isBlank()) {
            restClients.put("base", createRestClient(properties.baseRpcUrl()));
        }
        if (properties.ethereumRpcUrl() != null && !properties.ethereumRpcUrl().isBlank()) {
            restClients.put("ethereum", createRestClient(properties.ethereumRpcUrl()));
        }
    }

    private RestClient createRestClient(String rpcUrl) {
        var httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        return RestClient.builder()
                .baseUrl(rpcUrl)
                .requestFactory(requestFactory)
                .build();
    }

    record EvmChainConfig(long chainId, String rpcUrl, String usdcContract) {}

    record TxMapping(String txHash, String chain) {}

    record JsonRpcRequest(String jsonrpc, String method, Object[] params, int id) {

        JsonRpcRequest(String method, Object... params) {
            this("2.0", method, params, 1);
        }
    }
}
