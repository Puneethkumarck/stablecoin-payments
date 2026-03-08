package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.exception.ChainUnavailableException;
import com.stablecoin.payments.custody.domain.model.ChainCandidate;
import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.ChainSelectionResult;
import com.stablecoin.payments.custody.domain.model.ChainSelectionWeights;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import com.stablecoin.payments.custody.domain.model.WalletTier;
import com.stablecoin.payments.custody.domain.port.ChainFeeProvider;
import com.stablecoin.payments.custody.domain.port.ChainHealthProvider;
import com.stablecoin.payments.custody.domain.port.ChainSelectionLogRepository;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.stablecoin.payments.custody.fixtures.ChainSelectionFixtures.AMOUNT;
import static com.stablecoin.payments.custody.fixtures.ChainSelectionFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.ChainSelectionFixtures.CHAIN_ETHEREUM;
import static com.stablecoin.payments.custody.fixtures.ChainSelectionFixtures.CHAIN_SOLANA;
import static com.stablecoin.payments.custody.fixtures.ChainSelectionFixtures.TRANSFER_ID;
import static com.stablecoin.payments.custody.fixtures.ChainSelectionFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.ChainSelectionFixtures.aSelectionRequest;
import static com.stablecoin.payments.custody.fixtures.ChainSelectionFixtures.aSelectionRequestWithAmount;
import static com.stablecoin.payments.custody.fixtures.ChainSelectionFixtures.aSelectionRequestWithPreferredChain;
import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.aBalanceWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChainSelectionEngineTest {

    @Mock
    private ChainHealthProvider chainHealthProvider;

    @Mock
    private ChainFeeProvider chainFeeProvider;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletBalanceRepository walletBalanceRepository;

    @Mock
    private ChainSelectionLogRepository chainSelectionLogRepository;

    @InjectMocks
    private ChainSelectionEngine engine;

    // -- Helper: create default weights and inject via constructor --

    private ChainSelectionEngine engineWith(ChainSelectionWeights weights) {
        return new ChainSelectionEngine(
                weights, chainHealthProvider, chainFeeProvider,
                walletRepository, walletBalanceRepository, chainSelectionLogRepository);
    }

    private void stubAllChainsHealthy() {
        given(chainHealthProvider.getHealthScore(CHAIN_BASE)).willReturn(1.0);
        given(chainHealthProvider.getHealthScore(CHAIN_ETHEREUM)).willReturn(1.0);
        given(chainHealthProvider.getHealthScore(CHAIN_SOLANA)).willReturn(1.0);
    }

    private void stubAllChainsFees() {
        given(chainFeeProvider.estimateFeeUsd(CHAIN_BASE, USDC)).willReturn(0.01);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_ETHEREUM, USDC)).willReturn(2.50);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_SOLANA, USDC)).willReturn(0.005);
    }

    private void stubAllChainsWithSufficientBalance() {
        stubChainWithBalance(CHAIN_BASE, AMOUNT, true);
        stubChainWithBalance(CHAIN_ETHEREUM, AMOUNT, true);
        stubChainWithBalance(CHAIN_SOLANA, AMOUNT, true);
    }

    private Wallet createWallet(ChainId chainId) {
        return Wallet.create(
                chainId,
                "0x" + chainId.value() + "Address1234567890abcdef",
                "0x" + chainId.value() + "Address1234567890AbCdEf",
                WalletTier.HOT, WalletPurpose.ON_RAMP,
                "fireblocks", "vault-" + chainId.value(),
                USDC);
    }

    private void stubChainWithBalance(ChainId chainId, BigDecimal amount, boolean sufficient) {
        var wallet = createWallet(chainId);
        given(walletRepository.findByChainIdAndPurpose(chainId, WalletPurpose.ON_RAMP))
                .willReturn(List.of(wallet));
        var balance = sufficient
                ? aBalanceWith(amount.add(BigDecimal.TEN), BigDecimal.ZERO)
                : aBalanceWith(BigDecimal.ONE, BigDecimal.ZERO);
        given(walletBalanceRepository.findByWalletIdAndStablecoin(wallet.walletId(), USDC))
                .willReturn(Optional.of(balance));
    }

    // ----------------------------------------------------------------
    // Tests
    // ----------------------------------------------------------------

    @Test
    void selectChain_baseWinsOnLowCost() {
        // Base: fee=0.01 (cheapest for its finality); Solana fee=0.005 but speed dominates less with default weights
        // Default weights: cost=0.4, speed=0.35, reliability=0.25
        // Base: 0.4*(1/0.01) + 0.35*(1/12) + 0.25*1.0 = 40.0 + 0.0292 + 0.25 = 40.279
        // Ethereum: 0.4*(1/2.50) + 0.35*(1/300) + 0.25*1.0 = 0.16 + 0.00117 + 0.25 = 0.411
        // Solana: 0.4*(1/0.005) + 0.35*(1/5) + 0.25*1.0 = 80.0 + 0.07 + 0.25 = 80.32
        // Actually Solana wins! Let me adjust fees to make Base win:
        // Make Solana more expensive and Base cheapest with speed factoring in
        var weights = ChainSelectionWeights.builder()
                .costWeight(0.5)
                .speedWeight(0.25)
                .reliabilityWeight(0.25)
                .build();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        stubAllChainsHealthy();
        // Set fees so Base wins: Base cheap with decent speed
        given(chainFeeProvider.estimateFeeUsd(CHAIN_BASE, USDC)).willReturn(0.01);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_ETHEREUM, USDC)).willReturn(2.50);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_SOLANA, USDC)).willReturn(0.02);

        var result = sut.selectChain(aSelectionRequest());

        var expected = ChainSelectionResult.builder()
                .selectedChain(CHAIN_BASE)
                .transferId(TRANSFER_ID)
                .candidates(result.candidates())
                .build();
        assertThat(result).usingRecursiveComparison()
                .ignoringFields("candidates")
                .isEqualTo(expected);
    }

    @Test
    void selectChain_solanaWinsOnSpeed() {
        // Speed weight dominates
        var weights = ChainSelectionWeights.builder()
                .costWeight(0.05)
                .speedWeight(0.9)
                .reliabilityWeight(0.05)
                .build();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        stubAllChainsHealthy();
        stubAllChainsFees();

        var result = sut.selectChain(aSelectionRequest());

        // Solana has 5s finality (fastest) → highest speed score
        var expected = ChainSelectionResult.builder()
                .selectedChain(CHAIN_SOLANA)
                .transferId(TRANSFER_ID)
                .candidates(result.candidates())
                .build();
        assertThat(result).usingRecursiveComparison()
                .ignoringFields("candidates")
                .isEqualTo(expected);
    }

    @Test
    void selectChain_ethereumWinsForHighValue() {
        // Only Ethereum has sufficient balance for a large transfer
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);
        var highAmount = new BigDecimal("500000.00");

        // Only Ethereum has sufficient balance
        stubChainWithBalance(CHAIN_ETHEREUM, highAmount, true);
        stubChainWithBalance(CHAIN_BASE, highAmount, false);
        stubChainWithBalance(CHAIN_SOLANA, highAmount, false);
        given(chainHealthProvider.getHealthScore(CHAIN_ETHEREUM)).willReturn(1.0);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_ETHEREUM, USDC)).willReturn(2.50);

        var result = sut.selectChain(aSelectionRequestWithAmount(highAmount));

        var expected = ChainSelectionResult.builder()
                .selectedChain(CHAIN_ETHEREUM)
                .transferId(TRANSFER_ID)
                .candidates(result.candidates())
                .build();
        assertThat(result).usingRecursiveComparison()
                .ignoringFields("candidates")
                .isEqualTo(expected);
    }

    @Test
    void selectChain_unhealthyChainExcluded() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        // Base is unhealthy
        given(chainHealthProvider.getHealthScore(CHAIN_BASE)).willReturn(0.0);
        given(chainHealthProvider.getHealthScore(CHAIN_ETHEREUM)).willReturn(1.0);
        given(chainHealthProvider.getHealthScore(CHAIN_SOLANA)).willReturn(1.0);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_ETHEREUM, USDC)).willReturn(2.50);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_SOLANA, USDC)).willReturn(0.005);

        var result = sut.selectChain(aSelectionRequest());

        // Base should not be in candidates at all
        assertThat(result.candidates().stream().map(c -> c.chainId().value()).toList())
                .doesNotContain("base");
    }

    @Test
    void selectChain_allChainsUnhealthy_throwsException() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        given(chainHealthProvider.getHealthScore(CHAIN_BASE)).willReturn(0.0);
        given(chainHealthProvider.getHealthScore(CHAIN_ETHEREUM)).willReturn(0.0);
        given(chainHealthProvider.getHealthScore(CHAIN_SOLANA)).willReturn(0.0);

        assertThatThrownBy(() -> sut.selectChain(aSelectionRequest()))
                .isInstanceOf(ChainUnavailableException.class)
                .hasMessageContaining("No healthy chain");
    }

    @Test
    void selectChain_preferredChainSelected() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        stubAllChainsHealthy();
        stubAllChainsFees();

        var result = sut.selectChain(aSelectionRequestWithPreferredChain("base"));

        var expected = ChainSelectionResult.builder()
                .selectedChain(CHAIN_BASE)
                .transferId(TRANSFER_ID)
                .candidates(result.candidates())
                .build();
        assertThat(result).usingRecursiveComparison()
                .ignoringFields("candidates")
                .isEqualTo(expected);
    }

    @Test
    void selectChain_preferredChainUnhealthy_fallsBackToScoring() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        // Base is unhealthy but is preferred
        given(chainHealthProvider.getHealthScore(CHAIN_BASE)).willReturn(0.0);
        given(chainHealthProvider.getHealthScore(CHAIN_ETHEREUM)).willReturn(1.0);
        given(chainHealthProvider.getHealthScore(CHAIN_SOLANA)).willReturn(1.0);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_ETHEREUM, USDC)).willReturn(2.50);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_SOLANA, USDC)).willReturn(0.005);

        var result = sut.selectChain(aSelectionRequestWithPreferredChain("base"));

        // Base is unhealthy, so it falls back to scoring. Solana should win.
        assertThat(result.selectedChain()).isNotEqualTo(CHAIN_BASE);
    }

    @Test
    void selectChain_insufficientBalance_chainExcluded() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        // Base has insufficient balance
        stubChainWithBalance(CHAIN_BASE, AMOUNT, false);
        stubChainWithBalance(CHAIN_ETHEREUM, AMOUNT, true);
        stubChainWithBalance(CHAIN_SOLANA, AMOUNT, true);
        given(chainHealthProvider.getHealthScore(CHAIN_ETHEREUM)).willReturn(1.0);
        given(chainHealthProvider.getHealthScore(CHAIN_SOLANA)).willReturn(1.0);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_ETHEREUM, USDC)).willReturn(2.50);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_SOLANA, USDC)).willReturn(0.005);

        var result = sut.selectChain(aSelectionRequest());

        assertThat(result.candidates().stream().map(c -> c.chainId().value()).toList())
                .doesNotContain("base");
    }

    @Test
    void selectChain_logsSelectionResult() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        stubAllChainsHealthy();
        stubAllChainsFees();

        var result = sut.selectChain(aSelectionRequest());

        then(chainSelectionLogRepository).should().save(TRANSFER_ID, result);
    }

    @Test
    void selectChain_allCandidatesScored() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        stubAllChainsHealthy();
        stubAllChainsFees();

        var result = sut.selectChain(aSelectionRequest());

        // All 3 MVP chains should appear as candidates
        assertThat(result.candidates()).hasSize(3);
    }

    @Test
    void selectChain_singleHealthyChain_selectedByDefault() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        // Only Ethereum is healthy and has balance
        stubChainWithBalance(CHAIN_BASE, AMOUNT, false);
        stubChainWithBalance(CHAIN_ETHEREUM, AMOUNT, true);
        stubChainWithBalance(CHAIN_SOLANA, AMOUNT, false);
        given(chainHealthProvider.getHealthScore(CHAIN_ETHEREUM)).willReturn(1.0);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_ETHEREUM, USDC)).willReturn(2.50);

        var result = sut.selectChain(aSelectionRequest());

        var expected = ChainSelectionResult.builder()
                .selectedChain(CHAIN_ETHEREUM)
                .transferId(TRANSFER_ID)
                .candidates(result.candidates())
                .build();
        assertThat(result).usingRecursiveComparison()
                .ignoringFields("candidates")
                .isEqualTo(expected);
    }

    @Test
    void selectChain_noWalletForChain_chainExcluded() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        // Base has no wallet at all
        given(walletRepository.findByChainIdAndPurpose(CHAIN_BASE, WalletPurpose.ON_RAMP))
                .willReturn(List.of());
        stubChainWithBalance(CHAIN_ETHEREUM, AMOUNT, true);
        stubChainWithBalance(CHAIN_SOLANA, AMOUNT, true);
        given(chainHealthProvider.getHealthScore(CHAIN_ETHEREUM)).willReturn(1.0);
        given(chainHealthProvider.getHealthScore(CHAIN_SOLANA)).willReturn(1.0);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_ETHEREUM, USDC)).willReturn(2.50);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_SOLANA, USDC)).willReturn(0.005);

        var result = sut.selectChain(aSelectionRequest());

        assertThat(result.candidates().stream().map(c -> c.chainId().value()).toList())
                .doesNotContain("base");
    }

    @Test
    void selectChain_scoringFormula_correctCalculation() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        // Only Base is available to make the test deterministic
        stubChainWithBalance(CHAIN_BASE, AMOUNT, true);
        stubChainWithBalance(CHAIN_ETHEREUM, AMOUNT, false);
        stubChainWithBalance(CHAIN_SOLANA, AMOUNT, false);
        given(chainHealthProvider.getHealthScore(CHAIN_BASE)).willReturn(1.0);
        given(chainFeeProvider.estimateFeeUsd(CHAIN_BASE, USDC)).willReturn(0.01);

        var result = sut.selectChain(aSelectionRequest());

        // Expected score: 0.4*(1/0.01) + 0.35*(1/12) + 0.25*1.0
        // = 0.4*100 + 0.35*0.08333 + 0.25
        // = 40.0 + 0.02917 + 0.25 = 40.27917
        double expectedScore = 0.4 * (1.0 / 0.01) + 0.35 * (1.0 / 12) + 0.25 * 1.0;

        var expectedCandidate = ChainCandidate.builder()
                .chainId(CHAIN_BASE)
                .feeUsd(0.01)
                .finalitySeconds(12)
                .healthScore(1.0)
                .score(expectedScore)
                .selected(true)
                .build();

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst()).usingRecursiveComparison()
                .withComparatorForType(Double::compare, Double.class)
                .isEqualTo(expectedCandidate);
    }

    @Test
    void selectChain_resultContainsSelectedFlag() {
        var weights = ChainSelectionWeights.defaults();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        stubAllChainsHealthy();
        stubAllChainsFees();

        var result = sut.selectChain(aSelectionRequest());

        // Exactly one candidate should have selected=true
        long selectedCount = result.candidates().stream().filter(ChainCandidate::selected).count();
        assertThat(selectedCount).isEqualTo(1L);
        // The selected candidate's chainId should match result.selectedChain()
        var selectedCandidate = result.candidates().stream()
                .filter(ChainCandidate::selected)
                .findFirst()
                .orElseThrow();
        assertThat(selectedCandidate.chainId()).isEqualTo(result.selectedChain());
    }

    @Test
    void selectChain_costWeightZero_speedDominates() {
        // Zero cost weight, high speed weight → fastest chain wins
        var weights = ChainSelectionWeights.builder()
                .costWeight(0.0)
                .speedWeight(0.75)
                .reliabilityWeight(0.25)
                .build();
        var sut = engineWith(weights);

        stubAllChainsWithSufficientBalance();
        stubAllChainsHealthy();
        stubAllChainsFees();

        var result = sut.selectChain(aSelectionRequest());

        // Solana has 5s finality (fastest) → highest speed score → should win
        var expected = ChainSelectionResult.builder()
                .selectedChain(CHAIN_SOLANA)
                .transferId(TRANSFER_ID)
                .candidates(result.candidates())
                .build();
        assertThat(result).usingRecursiveComparison()
                .ignoringFields("candidates")
                .isEqualTo(expected);
    }
}
