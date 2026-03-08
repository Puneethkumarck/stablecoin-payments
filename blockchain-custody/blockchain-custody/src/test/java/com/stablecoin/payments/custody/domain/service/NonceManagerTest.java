package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.port.NonceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.stablecoin.payments.custody.fixtures.NonceFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.NonceFixtures.CHAIN_ETHEREUM;
import static com.stablecoin.payments.custody.fixtures.NonceFixtures.CHAIN_POLYGON;
import static com.stablecoin.payments.custody.fixtures.NonceFixtures.CHAIN_SOLANA;
import static com.stablecoin.payments.custody.fixtures.NonceFixtures.WALLET_ID;
import static com.stablecoin.payments.custody.fixtures.NonceFixtures.aNotApplicableAssignment;
import static com.stablecoin.payments.custody.fixtures.NonceFixtures.aReusedAssignment;
import static com.stablecoin.payments.custody.fixtures.NonceFixtures.anIncrementedAssignment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("NonceManager")
class NonceManagerTest {

    @Mock
    private NonceRepository nonceRepository;

    @InjectMocks
    private NonceManager nonceManager;

    // -- EVM Fresh Nonce --------------------------------------------------

    @Test
    @DisplayName("should assign fresh nonce for Base chain")
    void shouldAssignFreshNonceForBaseChain() {
        // given
        given(nonceRepository.assignNextNonce(WALLET_ID, CHAIN_BASE)).willReturn(5L);

        // when
        var result = nonceManager.assignNonce(WALLET_ID, CHAIN_BASE, false);

        // then
        var expected = anIncrementedAssignment(5L);
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("should assign fresh nonce for Ethereum chain")
    void shouldAssignFreshNonceForEthereumChain() {
        // given
        given(nonceRepository.assignNextNonce(WALLET_ID, CHAIN_ETHEREUM)).willReturn(0L);

        // when
        var result = nonceManager.assignNonce(WALLET_ID, CHAIN_ETHEREUM, false);

        // then
        var expected = anIncrementedAssignment(0L);
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("should assign fresh nonce for Polygon chain")
    void shouldAssignFreshNonceForPolygonChain() {
        // given
        given(nonceRepository.assignNextNonce(WALLET_ID, CHAIN_POLYGON)).willReturn(10L);

        // when
        var result = nonceManager.assignNonce(WALLET_ID, CHAIN_POLYGON, false);

        // then
        var expected = anIncrementedAssignment(10L);
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("should delegate to repository for nonce assignment")
    void shouldDelegateToRepositoryForNonceAssignment() {
        // given
        given(nonceRepository.assignNextNonce(WALLET_ID, CHAIN_BASE)).willReturn(3L);

        // when
        nonceManager.assignNonce(WALLET_ID, CHAIN_BASE, false);

        // then
        then(nonceRepository).should().assignNextNonce(WALLET_ID, CHAIN_BASE);
    }

    // -- EVM Resubmit (replace-by-fee) ------------------------------------

    @Test
    @DisplayName("should reuse nonce on resubmit for EVM chain")
    void shouldReuseNonceOnResubmit() {
        // given — current_nonce in DB is 6 (next nonce to use), so resubmit reuses 5
        given(nonceRepository.getCurrentNonce(WALLET_ID, CHAIN_BASE)).willReturn(Optional.of(6L));

        // when
        var result = nonceManager.assignNonce(WALLET_ID, CHAIN_BASE, true);

        // then
        var expected = aReusedAssignment(5L);
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("should not increment nonce on resubmit")
    void shouldNotIncrementNonceOnResubmit() {
        // given
        given(nonceRepository.getCurrentNonce(WALLET_ID, CHAIN_BASE)).willReturn(Optional.of(3L));

        // when
        nonceManager.assignNonce(WALLET_ID, CHAIN_BASE, true);

        // then
        then(nonceRepository).should(never()).assignNextNonce(WALLET_ID, CHAIN_BASE);
    }

    @Test
    @DisplayName("should throw when resubmit but no existing nonce")
    void shouldThrowWhenResubmitButNoExistingNonce() {
        // given
        given(nonceRepository.getCurrentNonce(WALLET_ID, CHAIN_BASE)).willReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> nonceManager.assignNonce(WALLET_ID, CHAIN_BASE, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No existing nonce found");
    }

    // -- Solana (non-nonce chain) -----------------------------------------

    @Test
    @DisplayName("should return NOT_APPLICABLE for Solana")
    void shouldReturnNotApplicableForSolana() {
        // when
        var result = nonceManager.assignNonce(WALLET_ID, CHAIN_SOLANA, false);

        // then
        var expected = aNotApplicableAssignment();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("should not call repository for Solana")
    void shouldNotCallRepositoryForSolana() {
        // when
        nonceManager.assignNonce(WALLET_ID, CHAIN_SOLANA, false);

        // then
        then(nonceRepository).should(never()).assignNextNonce(WALLET_ID, CHAIN_SOLANA);
        then(nonceRepository).should(never()).getCurrentNonce(WALLET_ID, CHAIN_SOLANA);
    }

    @Test
    @DisplayName("should return NOT_APPLICABLE for Solana even on resubmit")
    void shouldReturnNotApplicableForSolanaEvenOnResubmit() {
        // when
        var result = nonceManager.assignNonce(WALLET_ID, CHAIN_SOLANA, true);

        // then
        var expected = aNotApplicableAssignment();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    // -- Validation -------------------------------------------------------

    @Test
    @DisplayName("should throw when walletId is null")
    void shouldThrowWhenWalletIdIsNull() {
        // when/then
        assertThatThrownBy(() -> nonceManager.assignNonce(null, CHAIN_BASE, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("walletId is required");
    }

    @Test
    @DisplayName("should throw when chainId is null")
    void shouldThrowWhenChainIdIsNull() {
        // when/then
        assertThatThrownBy(() -> nonceManager.assignNonce(WALLET_ID, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chainId is required");
    }
}
