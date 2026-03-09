package com.stablecoin.payments.custody.application.scheduler;

import com.stablecoin.payments.custody.domain.service.BalanceSyncCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that syncs blockchain balances from RPC every 30 seconds.
 * <p>
 * Delegates all business logic to {@link BalanceSyncCommandHandler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.balance.sync.enabled", havingValue = "true", matchIfMissing = true)
public class BalanceSyncJob {

    private final BalanceSyncCommandHandler balanceSyncCommandHandler;

    @Scheduled(fixedDelayString = "${app.balance.sync.interval-ms:30000}")
    public void syncBalances() {
        log.debug("Balance sync job triggered");
        balanceSyncCommandHandler.syncAllBalances();
    }
}
