package com.connecthub.notification.service;

import com.connecthub.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationCleanupScheduler.
 *
 * WHAT WE ARE TESTING:
 *   NotificationCleanupScheduler runs daily at 3 AM to delete notifications older
 *   than 90 days. Without this cleanup the "notifications" table grows forever,
 *   making queries slower and wasting database storage.
 *
 * RETENTION POLICY EXPLAINED:
 *   90-day retention means: if you last logged in 3 months ago, your old notifications
 *   will be gone when you come back. This is an explicit product decision to keep
 *   only recent notifications and keep the DB lean.
 *
 * HOW WE TEST A SCHEDULED METHOD:
 *   The @Scheduled(cron = "0 0 3 * * *") annotation is handled by the Spring
 *   framework, not by the method itself. In unit tests we bypass the scheduler
 *   and call cleanupOldNotifications() directly to test its logic in isolation.
 *   To test that the cron string is correct, you would use an integration test
 *   with Spring's CronExpression.parse() utility — not needed here.
 *
 * PATTERN USED:
 *   Mockito + @ExtendWith(MockitoExtension.class) — same pattern as the rest
 *   of the backend tests. No Spring context, no database — pure unit tests.
 */
@ExtendWith(MockitoExtension.class)
class NotificationCleanupSchedulerTest {

    /*
     * @Mock: NotificationRepository is mocked so we don't need a real database.
     * The mock records calls and returns whatever we configure it to return.
     */
    @Mock
    private NotificationRepository repo;

    /*
     * @InjectMocks: creates a real NotificationCleanupScheduler with the mock repo injected.
     */
    @InjectMocks
    private NotificationCleanupScheduler scheduler;

    // ── method invocation ───────────────────────────────────────────────────

    @Test
    void cleanupOldNotifications_callsDeleteOlderThan() {
        /*
         * Basic smoke test: running the cleanup must call deleteOlderThan() on the repo.
         * If someone removes this call from the scheduler, old notifications will pile up.
         */
        when(repo.deleteOlderThan(any())).thenReturn(0L);

        scheduler.cleanupOldNotifications();

        verify(repo).deleteOlderThan(any(LocalDateTime.class));
    }

    @Test
    void cleanupOldNotifications_callsDeleteExactlyOnce() {
        /*
         * The cleanup should delete in a single bulk DELETE query, not in a loop.
         * If deleteOlderThan were called N times, it would be an O(N) operation
         * instead of a single SQL statement — a performance bug.
         */
        when(repo.deleteOlderThan(any())).thenReturn(10L);

        scheduler.cleanupOldNotifications();

        verify(repo, times(1)).deleteOlderThan(any(LocalDateTime.class));
    }

    // ── threshold correctness ───────────────────────────────────────────────

    @Test
    void cleanupOldNotifications_thresholdIsApproximately90DaysAgo() {
        /*
         * This is the most important test: the threshold date must represent exactly
         * 90 days in the past. If it's wrong (e.g., 9 days), we'd delete recent
         * notifications. If it's wrong (e.g., 900 days), we'd keep too many.
         *
         * We capture the LocalDateTime argument passed to deleteOlderThan() and assert
         * it falls within a ±5 second window centred on "90 days ago from now".
         *
         * WHY ±5 SECONDS: The test code runs before scheduler.cleanupOldNotifications(),
         * so there's a tiny time difference between our "expected threshold" and the
         * one computed inside the scheduler. ±5s prevents millisecond-level flakiness.
         */
        when(repo.deleteOlderThan(any())).thenReturn(0L);

        LocalDateTime before = LocalDateTime.now().minusDays(90).minusSeconds(5);
        LocalDateTime after  = LocalDateTime.now().minusDays(90).plusSeconds(5);

        scheduler.cleanupOldNotifications();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).deleteOlderThan(captor.capture());

        LocalDateTime threshold = captor.getValue();
        assertTrue(threshold.isAfter(before),
            "Threshold should be at most 5s earlier than 90 days ago");
        assertTrue(threshold.isBefore(after),
            "Threshold should be at most 5s later than 90 days ago");
    }

    @Test
    void cleanupOldNotifications_thresholdIsNot30DaysAgo() {
        /*
         * Negative test: verify the scheduler uses the 90-day retention policy,
         * not some other common value like 30 days. If the constant in
         * NotificationCleanupScheduler.RETENTION_DAYS is accidentally set to 30,
         * this test will fail and alert the developer.
         */
        when(repo.deleteOlderThan(any())).thenReturn(0L);

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        scheduler.cleanupOldNotifications();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).deleteOlderThan(captor.capture());

        /*
         * The captured threshold should be BEFORE thirtyDaysAgo (further in the past).
         * If the threshold equals ~30 days ago, someone changed the retention policy
         * without updating this test — that's intentional friction to force a review.
         */
        assertTrue(captor.getValue().isBefore(thirtyDaysAgo),
            "Threshold must be 90 days ago, not 30 days ago — RETENTION_DAYS should be 90");
    }

    // ── edge cases ──────────────────────────────────────────────────────────

    @Test
    void cleanupOldNotifications_zeroDeleted_completesNormally() {
        /*
         * When no old notifications exist, the method should return without logging
         * anything (the implementation checks deleted > 0 before logging).
         * This test verifies no exception is thrown when the count is zero.
         */
        when(repo.deleteOlderThan(any())).thenReturn(0L);

        assertDoesNotThrow(() -> scheduler.cleanupOldNotifications());
    }

    @Test
    void cleanupOldNotifications_largeCount_completesNormally() {
        /*
         * If 10,000 old notifications are deleted in one run, the scheduler must
         * handle the large long value returned by the repository without overflow.
         */
        when(repo.deleteOlderThan(any())).thenReturn(10_000L);

        assertDoesNotThrow(() -> scheduler.cleanupOldNotifications());
        verify(repo, times(1)).deleteOlderThan(any(LocalDateTime.class));
    }

    @Test
    void cleanupOldNotifications_repositoryThrows_propagatesException() {
        /*
         * If the database is unavailable, deleteOlderThan() throws an exception.
         * The @Transactional annotation will roll back the (empty) transaction,
         * and Spring's @Scheduled framework will log the error and try again
         * on the next scheduled run. We verify the exception is not silently swallowed.
         */
        doThrow(new RuntimeException("DB timeout")).when(repo).deleteOlderThan(any());

        assertThrows(RuntimeException.class,
            () -> scheduler.cleanupOldNotifications(),
            "Database errors should propagate so the transaction framework can handle them");
    }
}
