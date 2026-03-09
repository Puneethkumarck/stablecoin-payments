package com.stablecoin.payments.custody.domain.port;

/**
 * Domain port for transfer monitor configuration.
 */
public interface TransferMonitorProperties {

    /**
     * Returns the number of seconds before a SUBMITTED transfer is considered stuck.
     */
    int resubmitTimeoutS();

    /**
     * Returns the maximum number of submission attempts before a transfer is failed.
     */
    int maxAttempts();

    /**
     * Returns the number of seconds a CONFIRMING transfer can remain without a receipt
     * before being marked for resubmission (e.g., after a chain reorg).
     * Defaults to 300 seconds (5 minutes).
     */
    default int confirmingTimeoutS() {
        return 300;
    }
}
