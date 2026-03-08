package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.AbstractIntegrationTest;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.TransferStatus;
import com.stablecoin.payments.custody.domain.model.TransferType;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.domain.port.ChainTransferRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.AMOUNT;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.CORRELATION_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.FROM_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.PARENT_TRANSFER_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.PAYMENT_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TO_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TX_HASH;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TX_HASH_RESUBMIT;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.anActiveWallet;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChainTransferPersistenceAdapter IT")
class ChainTransferPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private ChainTransferRepository adapter;

    @Autowired
    private WalletRepository walletAdapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve pending transfer with null chain id")
    void shouldSaveAndRetrievePendingTransfer() {
        var wallet = saveWallet();
        var transfer = createPendingTransfer(wallet.walletId());
        var saved = adapter.save(transfer);

        assertThat(adapter.findById(saved.transferId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find by payment id and type")
    void shouldFindByPaymentIdAndType() {
        var wallet = saveWallet();
        var transfer = createPendingTransfer(wallet.walletId());
        var saved = adapter.save(transfer);

        assertThat(adapter.findByPaymentIdAndType(PAYMENT_ID, TransferType.FORWARD)).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty when id not found")
    void shouldReturnEmptyWhenIdNotFound() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should return empty when payment id and type not found")
    void shouldReturnEmptyWhenPaymentIdAndTypeNotFound() {
        assertThat(adapter.findByPaymentIdAndType(UUID.randomUUID(), TransferType.FORWARD)).isEmpty();
    }

    @Test
    @DisplayName("should find by status")
    void shouldFindByStatus() {
        var wallet = saveWallet();
        var transfer = createPendingTransfer(wallet.walletId());
        adapter.save(transfer);

        assertThat(adapter.findByStatus(TransferStatus.PENDING)).hasSize(1);
        assertThat(adapter.findByStatus(TransferStatus.CONFIRMED)).isEmpty();
    }

    // ── State Machine Happy Path ─────────────────────────────────────────

    @Test
    @DisplayName("should update transfer through full happy path to CONFIRMED")
    void shouldUpdateTransferThroughFullHappyPath() {
        var wallet = saveWallet();
        var transfer = createPendingTransfer(wallet.walletId());
        var saved = adapter.save(transfer);

        saved = adapter.save(saved.selectChain(CHAIN_BASE));
        saved = adapter.save(saved.startSigning(42L));
        saved = adapter.save(saved.submit(TX_HASH));
        saved = adapter.save(saved.startConfirming());
        var expected = adapter.save(
                saved.confirm(12345L, 15, new BigDecimal("21000"), new BigDecimal("25.500000000")));

        assertThat(adapter.findById(transfer.transferId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should update transfer through resubmission path")
    void shouldUpdateTransferThroughResubmissionPath() {
        var wallet = saveWallet();
        var transfer = createPendingTransfer(wallet.walletId());
        var saved = adapter.save(transfer);

        saved = adapter.save(saved.selectChain(CHAIN_BASE));
        saved = adapter.save(saved.startSigning(42L));
        saved = adapter.save(saved.submit(TX_HASH));
        saved = adapter.save(saved.markForResubmission());
        var expected = adapter.save(saved.resubmit(TX_HASH_RESUBMIT));

        assertThat(adapter.findById(transfer.transferId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    // ── Failure Path ─────────────────────────────────────────────────────

    @Test
    @DisplayName("should update transfer through failure path")
    void shouldUpdateTransferThroughFailurePath() {
        var wallet = saveWallet();
        var transfer = createPendingTransfer(wallet.walletId());
        var saved = adapter.save(transfer);
        saved = adapter.save(saved.selectChain(CHAIN_BASE));
        var expected = adapter.save(saved.fail("Insufficient gas", "GAS_LIMIT_EXCEEDED"));

        assertThat(adapter.findById(transfer.transferId())).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    // ── Return Transfer ──────────────────────────────────────────────────

    @Test
    @DisplayName("should persist return transfer with parent id")
    void shouldPersistReturnTransferWithParentId() {
        var wallet = saveWallet();
        var transfer = ChainTransfer.initiate(
                PAYMENT_ID, CORRELATION_ID, TransferType.RETURN, PARENT_TRANSFER_ID,
                USDC, AMOUNT, wallet.walletId(), TO_ADDRESS, FROM_ADDRESS);
        var saved = adapter.save(transfer);

        assertThat(adapter.findByPaymentIdAndType(PAYMENT_ID, TransferType.RETURN)).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Wallet saveWallet() {
        return walletAdapter.save(anActiveWallet());
    }

    private ChainTransfer createPendingTransfer(UUID fromWalletId) {
        return ChainTransfer.initiate(
                PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                USDC, AMOUNT, fromWalletId, TO_ADDRESS, FROM_ADDRESS);
    }
}
