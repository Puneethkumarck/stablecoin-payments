package com.stablecoin.payments.custody.application.scheduler;

import com.stablecoin.payments.custody.domain.service.TransferMonitorCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that polls in-flight chain transfers every 15 seconds.
 * <p>
 * Delegates all business logic to {@link TransferMonitorCommandHandler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.transfer.monitor.enabled", havingValue = "true", matchIfMissing = true)
public class TransferMonitorJob {

    private final TransferMonitorCommandHandler transferMonitorCommandHandler;

    @Scheduled(fixedDelayString = "${app.transfer.monitor.interval-ms:15000}")
    public void pollTransfers() {
        log.debug("Transfer monitor job triggered");
        transferMonitorCommandHandler.monitorPendingTransfers();
    }
}
