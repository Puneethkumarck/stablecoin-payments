package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.AbstractIntegrationTest;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.ParticipantType;
import com.stablecoin.payments.custody.domain.model.TransferParticipant;
import com.stablecoin.payments.custody.domain.model.TransferType;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.domain.port.ChainTransferRepository;
import com.stablecoin.payments.custody.domain.port.TransferParticipantRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.AMOUNT;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.CORRELATION_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.FROM_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.PAYMENT_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TO_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.anActiveWallet;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TransferParticipantPersistenceAdapter IT")
class TransferParticipantPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private TransferParticipantRepository adapter;

    @Autowired
    private ChainTransferRepository transferAdapter;

    @Autowired
    private WalletRepository walletAdapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve by transfer id")
    void shouldSaveAndRetrieveByTransferId() {
        var wallet = saveWallet();
        var transfer = saveTransfer(wallet.walletId());

        var participant = TransferParticipant.create(
                transfer.transferId(), ParticipantType.INPUT,
                FROM_ADDRESS, wallet.walletId(),
                new BigDecimal("1000.00"), "USDC");
        var saved = adapter.save(participant);

        var results = adapter.findByTransferId(transfer.transferId());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst())
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty list for unknown transfer id")
    void shouldReturnEmptyListForUnknownTransferId() {
        assertThat(adapter.findByTransferId(UUID.randomUUID())).isEmpty();
    }

    // ── Multiple Participants ────────────────────────────────────────────

    @Test
    @DisplayName("should save multiple participants for same transfer")
    void shouldSaveMultipleParticipantsForSameTransfer() {
        var wallet = saveWallet();
        var transfer = saveTransfer(wallet.walletId());

        adapter.save(TransferParticipant.create(
                transfer.transferId(), ParticipantType.INPUT,
                FROM_ADDRESS, wallet.walletId(),
                new BigDecimal("1000.00"), "USDC"));

        adapter.save(TransferParticipant.create(
                transfer.transferId(), ParticipantType.OUTPUT,
                TO_ADDRESS, null,
                new BigDecimal("1000.00"), "USDC"));

        adapter.save(TransferParticipant.create(
                transfer.transferId(), ParticipantType.FEE,
                FROM_ADDRESS, wallet.walletId(),
                new BigDecimal("0.50"), "ETH"));

        assertThat(adapter.findByTransferId(transfer.transferId())).hasSize(3);
    }

    // ── Enum Round-Trip ──────────────────────────────────────────────────

    @Test
    @DisplayName("should persist all participant types")
    void shouldPersistAllParticipantTypes() {
        var wallet = saveWallet();
        var transfer = saveTransfer(wallet.walletId());

        for (var type : ParticipantType.values()) {
            adapter.save(TransferParticipant.create(
                    transfer.transferId(), type,
                    FROM_ADDRESS, wallet.walletId(),
                    new BigDecimal("100.00"), "USDC"));
        }

        assertThat(adapter.findByTransferId(transfer.transferId()))
                .hasSize(ParticipantType.values().length);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Wallet saveWallet() {
        return walletAdapter.save(anActiveWallet());
    }

    private ChainTransfer saveTransfer(UUID fromWalletId) {
        return transferAdapter.save(ChainTransfer.initiate(
                PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                USDC, AMOUNT, fromWalletId, TO_ADDRESS, FROM_ADDRESS));
    }
}
