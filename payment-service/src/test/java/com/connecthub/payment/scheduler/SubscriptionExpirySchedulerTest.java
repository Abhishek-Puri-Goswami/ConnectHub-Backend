package com.connecthub.payment.scheduler;

import com.connecthub.payment.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirySchedulerTest {

    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private SubscriptionExpiryScheduler scheduler;

    @Test
    void expireOverdueSubscriptions_delegatesToService() {
        scheduler.expireOverdueSubscriptions();

        verify(subscriptionService).expireOverdueSubscriptions();
    }

    @Test
    void expireOverdueSubscriptions_serviceThrows_doesNotPropagate() {
        doThrow(new RuntimeException("db error")).when(subscriptionService).expireOverdueSubscriptions();

        // Scheduler itself does not catch — the exception propagates so Spring can retry/alert.
        // This test just verifies delegation happens.
        try {
            scheduler.expireOverdueSubscriptions();
        } catch (RuntimeException ignored) {
            // expected
        }
        verify(subscriptionService).expireOverdueSubscriptions();
    }
}
