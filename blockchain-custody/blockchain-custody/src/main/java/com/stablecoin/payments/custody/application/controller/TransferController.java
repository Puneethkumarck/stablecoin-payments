package com.stablecoin.payments.custody.application.controller;

import com.stablecoin.payments.custody.api.TransferRequest;
import com.stablecoin.payments.custody.api.TransferResponse;
import com.stablecoin.payments.custody.api.WalletBalanceResponse;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.TransferType;
import com.stablecoin.payments.custody.domain.service.TransferCommandHandler;
import com.stablecoin.payments.custody.domain.service.TransferCommandHandler.WalletBalanceDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.OK;

/**
 * Thin REST controller for blockchain transfer operations.
 * Delegates all business logic to {@link TransferCommandHandler}.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class TransferController {

    private final TransferCommandHandler transferCommandHandler;

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> submitTransfer(@Valid @RequestBody TransferRequest request) {
        var result = transferCommandHandler.initiateTransfer(
                request.paymentId(),
                request.correlationId(),
                TransferType.valueOf(request.transferType()),
                request.parentTransferId(),
                StablecoinTicker.of(request.stablecoin()),
                new BigDecimal(request.amount()),
                request.toWalletAddress(),
                request.preferredChain());

        return ResponseEntity
                .status(result.created() ? ACCEPTED : OK)
                .body(toTransferResponse(result.transfer()));
    }

    @GetMapping("/transfers/{transferId}")
    public TransferResponse getTransfer(@PathVariable UUID transferId) {
        var transfer = transferCommandHandler.getTransfer(transferId);
        return toTransferResponse(transfer);
    }

    @GetMapping("/wallets/{walletId}/balance")
    public WalletBalanceResponse getWalletBalance(@PathVariable UUID walletId) {
        var details = transferCommandHandler.getWalletBalance(walletId);
        return toWalletBalanceResponse(details);
    }

    private TransferResponse toTransferResponse(ChainTransfer transfer) {
        return new TransferResponse(
                transfer.transferId(),
                transfer.paymentId(),
                transfer.status().name(),
                transfer.chainId() != null ? transfer.chainId().value() : null,
                transfer.stablecoin() != null ? transfer.stablecoin().ticker() : null,
                transfer.amount() != null ? transfer.amount().toPlainString() : null,
                transfer.fromAddress(),
                transfer.toWalletAddress(),
                transfer.txHash(),
                transfer.blockNumber() != null ? transfer.blockNumber().toString() : null,
                transfer.confirmations(),
                transfer.gasUsed() != null ? transfer.gasUsed().toPlainString() : null,
                transfer.gasPriceGwei() != null ? transfer.gasPriceGwei().toPlainString() : null,
                transfer.chainId() != null ? estimatedConfirmationSeconds(transfer.chainId().value()) : null,
                transfer.blockConfirmedAt(),
                transfer.createdAt());
    }

    private WalletBalanceResponse toWalletBalanceResponse(WalletBalanceDetails details) {
        var wallet = details.wallet();
        var entries = details.balances().stream()
                .map(b -> new WalletBalanceResponse.BalanceEntry(
                        b.stablecoin().ticker(),
                        b.availableBalance().toPlainString(),
                        b.reservedBalance().toPlainString(),
                        b.blockchainBalance().toPlainString(),
                        String.valueOf(b.lastIndexedBlock())))
                .toList();

        var latestUpdate = details.balances().stream()
                .map(b -> b.updatedAt())
                .max(java.time.Instant::compareTo)
                .orElse(wallet.createdAt());

        return new WalletBalanceResponse(
                wallet.walletId(), wallet.address(), wallet.chainId().value(),
                entries, latestUpdate);
    }

    private Integer estimatedConfirmationSeconds(String chainId) {
        return switch (chainId) {
            case "base" -> 12;
            case "ethereum" -> 300;
            case "solana" -> 5;
            default -> null;
        };
    }
}
