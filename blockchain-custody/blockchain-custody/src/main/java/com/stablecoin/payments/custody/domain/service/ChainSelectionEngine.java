package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.exception.ChainUnavailableException;
import com.stablecoin.payments.custody.domain.model.ChainCandidate;
import com.stablecoin.payments.custody.domain.model.ChainConfig;
import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.ChainSelectionResult;
import com.stablecoin.payments.custody.domain.model.ChainSelectionWeights;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import com.stablecoin.payments.custody.domain.port.ChainFeeProvider;
import com.stablecoin.payments.custody.domain.port.ChainHealthProvider;
import com.stablecoin.payments.custody.domain.port.ChainSelectionLogRepository;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain service that evaluates candidate blockchain chains using a weighted scoring model
 * and selects the optimal chain for a given transfer.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Get MVP candidate chains (Base, Ethereum, Solana)</li>
 *   <li>Filter by wallet liquidity — chain must have an ON_RAMP wallet with sufficient balance</li>
 *   <li>Filter by health — chain must have health score &gt; 0</li>
 *   <li>Score each candidate: score = costWeight * (1/feeUsd) + speedWeight * (1/finalitySeconds) + reliabilityWeight * healthScore</li>
 *   <li>If preferredChain is set and healthy, select it regardless of score</li>
 *   <li>Otherwise select the candidate with the highest score</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainSelectionEngine {

    private final ChainSelectionWeights weights;
    private final ChainHealthProvider chainHealthProvider;
    private final ChainFeeProvider chainFeeProvider;
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final ChainSelectionLogRepository chainSelectionLogRepository;

    /**
     * Request record for chain selection.
     */
    public record ChainSelectionRequest(
            UUID transferId,
            StablecoinTicker stablecoin,
            BigDecimal amount,
            String preferredChain
    ) {

        public ChainSelectionRequest {
            if (transferId == null) {
                throw new IllegalArgumentException("transferId is required");
            }
            if (stablecoin == null) {
                throw new IllegalArgumentException("stablecoin is required");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }
    }

    /**
     * MVP candidate chain configurations.
     */
    private static final Map<String, ChainConfig> MVP_CHAINS = Map.of(
            "base", new ChainConfig(
                    new ChainId("base"), 1, 12, "ETH",
                    List.of("https://base-rpc.example.com"), "https://basescan.org"),
            "ethereum", new ChainConfig(
                    new ChainId("ethereum"), 32, 300, "ETH",
                    List.of("https://eth-rpc.example.com"), "https://etherscan.io"),
            "solana", new ChainConfig(
                    new ChainId("solana"), 1, 5, "SOL",
                    List.of("https://sol-rpc.example.com"), "https://explorer.solana.com")
    );

    /**
     * Selects the optimal chain for a transfer based on cost, speed, reliability,
     * wallet liquidity, and chain health.
     *
     * @param request the selection request containing transfer details
     * @return the selection result with the chosen chain and all scored candidates
     * @throws ChainUnavailableException if no healthy, funded chain is available
     */
    public ChainSelectionResult selectChain(ChainSelectionRequest request) {
        log.info("Selecting chain for transfer={}, stablecoin={}, amount={}",
                request.transferId(), request.stablecoin().ticker(), request.amount());

        var candidateConfigs = new ArrayList<>(MVP_CHAINS.values());

        // Filter and score candidates
        var scoredCandidates = candidateConfigs.stream()
                .filter(config -> hasWalletWithSufficientBalance(config.chainId(), request.stablecoin(), request.amount()))
                .filter(config -> chainHealthProvider.getHealthScore(config.chainId()) > 0)
                .map(config -> scoreCandidate(config, request.stablecoin()))
                .toList();

        if (scoredCandidates.isEmpty()) {
            throw new ChainUnavailableException(
                    "No healthy chain with sufficient balance available for transfer %s"
                            .formatted(request.transferId()));
        }

        // Determine selected chain
        ChainCandidate selectedCandidate = resolveSelectedCandidate(scoredCandidates, request.preferredChain());

        // Build final candidates list with selected flag
        var finalCandidates = scoredCandidates.stream()
                .map(candidate -> candidate.toBuilder()
                        .selected(candidate.chainId().equals(selectedCandidate.chainId()))
                        .build())
                .toList();

        var result = ChainSelectionResult.builder()
                .selectedChain(selectedCandidate.chainId())
                .candidates(finalCandidates)
                .transferId(request.transferId())
                .build();

        chainSelectionLogRepository.save(request.transferId(), result);

        log.info("Selected chain={} for transfer={} (candidates={})",
                result.selectedChain().value(), request.transferId(), finalCandidates.size());

        return result;
    }

    private boolean hasWalletWithSufficientBalance(ChainId chainId, StablecoinTicker stablecoin, BigDecimal amount) {
        var wallets = walletRepository.findByChainIdAndPurpose(chainId, WalletPurpose.ON_RAMP);
        if (wallets.isEmpty()) {
            log.debug("No ON_RAMP wallet found for chain={}", chainId.value());
            return false;
        }

        return wallets.stream().anyMatch(wallet -> {
            var balance = walletBalanceRepository.findByWalletIdAndStablecoin(wallet.walletId(), stablecoin);
            return balance.map(b -> b.hasSufficientBalance(amount)).orElse(false);
        });
    }

    private ChainCandidate scoreCandidate(ChainConfig config, StablecoinTicker stablecoin) {
        double feeUsd = chainFeeProvider.estimateFeeUsd(config.chainId(), stablecoin);
        double healthScore = chainHealthProvider.getHealthScore(config.chainId());

        double costScore = feeUsd > 0 ? 1.0 / feeUsd : 0.0;
        double speedScore = config.avgFinalitySeconds() > 0 ? 1.0 / config.avgFinalitySeconds() : 0.0;

        double score = weights.costWeight() * costScore
                + weights.speedWeight() * speedScore
                + weights.reliabilityWeight() * healthScore;

        return ChainCandidate.builder()
                .chainId(config.chainId())
                .feeUsd(feeUsd)
                .finalitySeconds(config.avgFinalitySeconds())
                .healthScore(healthScore)
                .score(score)
                .selected(false)
                .build();
    }

    private ChainCandidate resolveSelectedCandidate(List<ChainCandidate> candidates, String preferredChain) {
        if (preferredChain != null && !preferredChain.isBlank()) {
            var preferredChainId = new ChainId(preferredChain);
            var preferred = candidates.stream()
                    .filter(c -> c.chainId().equals(preferredChainId))
                    .findFirst();
            if (preferred.isPresent()) {
                return preferred.get();
            }
            log.warn("Preferred chain {} is not available, falling back to scoring", preferredChain);
        }
        return candidates.stream()
                .max(Comparator.comparingDouble(ChainCandidate::score))
                .orElseThrow(() -> new ChainUnavailableException("No candidates to select from"));
    }
}
