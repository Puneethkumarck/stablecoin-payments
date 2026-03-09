package com.stablecoin.payments.custody.domain.model;

/**
 * Wrapper to distinguish newly created transfers (202) from idempotent replays (200).
 */
public record TransferResult(ChainTransfer transfer, boolean created) {}
