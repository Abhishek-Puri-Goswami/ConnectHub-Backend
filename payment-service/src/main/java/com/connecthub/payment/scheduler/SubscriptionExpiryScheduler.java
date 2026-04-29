package com.connecthub.payment.scheduler;

import com.connecthub.payment.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs every hour and marks any ACTIVE Premium subscription whose endDate
 * has passed as EXPIRED, then fires a Kafka event so auth-service resets
 * the user's JWT tier claim back to FREE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryScheduler {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 0 * * * *")
    public void expireOverdueSubscriptions() {
        log.debug("Running subscription expiry check");
        subscriptionService.expireOverdueSubscriptions();
    }
}
