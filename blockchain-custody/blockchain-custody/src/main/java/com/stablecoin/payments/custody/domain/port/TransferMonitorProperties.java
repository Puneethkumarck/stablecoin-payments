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
}
