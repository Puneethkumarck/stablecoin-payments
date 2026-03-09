package com.stablecoin.payments.onramp.application.scheduler;

import com.stablecoin.payments.onramp.domain.port.CollectionOrderRepository;
import com.stablecoin.payments.onramp.domain.service.CollectionCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.AWAITING_CONFIRMATION;

/**
 * Scheduled job that expires collection orders that have been in
 * AWAITING_CONFIRMATION state past their {@code expiresAt} timestamp.
 * <p>
 * Thin delegator — all business logic (state transition, save, event publish)
 * is handled by {@link CollectionCommandHandler#expireCollection}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.onramp.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class CollectionExpiryJob {

    private final CollectionOrderRepository collectionOrderRepository;
    private final CollectionCommandHandler collectionCommandHandler;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.onramp.expiry.interval-ms:60000}")
    public void expireCollections() {
        var now = Instant.now(clock);
        var expiredOrders = collectionOrderRepository.findExpiredByStatus(AWAITING_CONFIRMATION, now);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Found {} expired collection orders to process", expiredOrders.size());

        for (var order : expiredOrders) {
            collectionCommandHandler.expireCollection(order, now);
        }

        log.info("Collection expiry job completed — expired={}", expiredOrders.size());
    }
}
