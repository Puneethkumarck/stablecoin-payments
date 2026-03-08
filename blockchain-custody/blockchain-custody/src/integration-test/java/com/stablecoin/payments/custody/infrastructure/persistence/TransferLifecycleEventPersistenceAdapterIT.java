package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.AbstractIntegrationTest;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.TransferLifecycleEvent;
import com.stablecoin.payments.custody.domain.model.TransferType;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.domain.port.ChainTransferRepository;
import com.stablecoin.payments.custody.domain.port.TransferLifecycleEventRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

@DisplayName("TransferLifecycleEventPersistenceAdapter IT")
class TransferLifecycleEventPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private TransferLifecycleEventRepository adapter;

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

        var event = TransferLifecycleEvent.record(transfer.transferId(), "PENDING");
        var saved = adapter.save(event);

        var results = adapter.findByTransferId(transfer.transferId());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst())
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty list for unknown transfer id")
    void shouldReturnEmptyListForUnknownTransferId() {
        assertThat(adapter.findByTransferId(UUID.randomUUID())).isEmpty();
    }

    // ── Multiple Events ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save multiple events for same transfer")
    void shouldSaveMultipleEventsForSameTransfer() {
        var wallet = saveWallet();
        var transfer = saveTransfer(wallet.walletId());

        adapter.save(TransferLifecycleEvent.record(transfer.transferId(), "PENDING"));
        adapter.save(TransferLifecycleEvent.record(transfer.transferId(), "CHAIN_SELECTED"));
        adapter.save(TransferLifecycleEvent.record(transfer.transferId(), "SIGNING"));

        assertThat(adapter.findByTransferId(transfer.transferId())).hasSize(3);
    }

    // ── Event with Participant Details ────────────────────────────────────

    @Test
    @DisplayName("should persist event with participant details")
    void shouldPersistEventWithParticipantDetails() {
        var wallet = saveWallet();
        var transfer = saveTransfer(wallet.walletId());

        var event = TransferLifecycleEvent.record(
                transfer.transferId(), "SUBMITTED", "INPUT", FROM_ADDRESS);
        var saved = adapter.save(event);

        var results = adapter.findByTransferId(transfer.transferId());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst())
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
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
