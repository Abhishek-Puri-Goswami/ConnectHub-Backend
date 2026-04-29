package com.connecthub.notification.service;

import com.connecthub.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * NotificationCleanupScheduler — Periodic Retention Cleanup
 *
 * PURPOSE:
 *   Deletes notifications older than 90 days to prevent the notification table
 *   from growing unboundedly. Runs daily at 3 AM. Old notifications that were
 *   already read (or never read) are permanently removed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationCleanupScheduler {

    private final NotificationRepository repo;

    private static final int RETENTION_DAYS = 90;

    @Scheduled(cron = "0 0 3 * * *") // daily at 3:00 AM
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);
        long deleted = repo.deleteOlderThan(threshold);
        if (deleted > 0) {
            log.info("Notification retention cleanup: deleted {} notifications older than {} days", deleted, RETENTION_DAYS);
        }
    }
}
