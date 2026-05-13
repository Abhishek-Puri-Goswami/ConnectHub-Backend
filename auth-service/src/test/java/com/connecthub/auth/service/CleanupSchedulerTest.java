package com.connecthub.auth.service;

import com.connecthub.auth.repository.UserRepository;
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
 * Unit tests for CleanupScheduler.
 *
 * WHAT WE ARE TESTING:
 * CleanupScheduler runs hourly (via @Scheduled) to delete stale accounts:
 * 1. Unverified accounts — users who registered but never confirmed their email
 * within 24h.
 *
 * Without this cleanup, the "users" table would grow endlessly with dead rows,
 * making queries slower and taking up database storage unnecessarily.
 *
 * WHY UNIT TESTS AND NOT INTEGRATION TESTS HERE:
 * We don't need a real database or a running scheduler daemon to test the
 * logic.
 * We just need to verify:
 * - The correct repository methods are called.
 * - The threshold date passed to those methods is approximately 24 hours ago.
 * Mocking the repository is faster, isolated, and doesn't require a DB
 * container.
 *
 * KEY CONCEPT — Testing Scheduled Methods:
 * 
 * @Scheduled tells Spring to call cleanupStaleAccounts() every hour
 *            automatically.
 *            In tests we call the method directly — we test the logic, not the
 *            scheduling itself.
 *            The Spring scheduler configuration is integration-tested at the
 *            infrastructure level.
 *
 *            KEY CONCEPT — Threshold Validation with Time Windows:
 *            Because LocalDateTime.now() is called inside
 *            cleanupStaleAccounts(), we can't
 *            predict the exact millisecond. Instead we calculate a "before" and
 *            "after" window
 *            (±5 seconds) and assert the captured threshold falls within that
 *            window.
 *            This avoids flaky tests from microsecond timing differences.
 */
@ExtendWith(MockitoExtension.class)
class CleanupSchedulerTest {

    /*
     * @Mock creates a fake UserRepository. The real one would need a running MySQL
     * instance.
     * We just want to verify which methods are called with which arguments.
     */
    @Mock
    private UserRepository userRepository;

    /*
     * @InjectMocks creates a real CleanupScheduler and injects the mock repository
     * into it.
     */
    @InjectMocks
    private CleanupScheduler scheduler;

    // ── method invocations ──────────────────────────────────────────────────

    @Test
    void cleanupStaleAccounts_callsDeleteUnverified() {
        /*
         * Act: run the scheduled cleanup.
         * Assert: verify the unverified-account deletion method was invoked.
         */
        scheduler.cleanupStaleAccounts();

        verify(userRepository).deleteByEmailVerifiedFalseAndCreatedAtBefore(any(LocalDateTime.class));
    }

    // ── threshold correctness ───────────────────────────────────────────────

    @Test
    void cleanupStaleAccounts_thresholdIsApproximately24HoursAgo() {
        /*
         * The threshold date determines HOW OLD an account must be before it's deleted.
         * If the threshold is wrong (e.g., 1 hour ago), we'd delete accounts too early.
         * If it's wrong (e.g., 48 hours ago), old stale accounts would accumulate.
         *
         * We capture the LocalDateTime passed to the repository method and verify
         * it falls within a ±5 second window of "exactly 24 hours ago".
         *
         * BEGINNER NOTE: Why ±5 seconds? Because between when we calculate "before"
         * and when the scheduler runs, a few milliseconds pass. ±5s is generous enough
         * to prevent flaky tests on slow machines while still catching real bugs.
         */
        // deleteByEmailVerifiedFalseAndCreatedAtBefore is void — no stub needed
        LocalDateTime before = LocalDateTime.now().minusHours(24).minusSeconds(5);
        LocalDateTime after = LocalDateTime.now().minusHours(24).plusSeconds(5);

        scheduler.cleanupStaleAccounts();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).deleteByEmailVerifiedFalseAndCreatedAtBefore(captor.capture());

        LocalDateTime threshold = captor.getValue();
        assertTrue(threshold.isAfter(before),
                "Threshold should be just before 24h ago (not deeper into the past)");
        assertTrue(threshold.isBefore(after),
                "Threshold should be just after 24h ago (not recent)");
    }

    // ── edge cases ──────────────────────────────────────────────────────────

    @Test
    void cleanupStaleAccounts_completesNormally() {
        assertDoesNotThrow(() -> scheduler.cleanupStaleAccounts());
    }

    @Test
    void cleanupStaleAccounts_repositoryThrows_propagatesException() {
        /*
         * If the database is unreachable, the repository method will throw.
         * The @Transactional annotation on cleanupStaleAccounts() will roll back
         * the transaction. We verify here that the exception propagates rather than
         * being silently swallowed.
         *
         * In production this would be caught by the @Scheduled framework and logged,
         * but we want to make sure the business code doesn't hide database errors.
         */
        doThrow(new RuntimeException("DB connection lost"))
                .when(userRepository).deleteByEmailVerifiedFalseAndCreatedAtBefore(any());

        assertThrows(RuntimeException.class, () -> scheduler.cleanupStaleAccounts(),
                "Repository exceptions should propagate so the transaction can roll back");
    }
}
