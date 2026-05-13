package com.connecthub.notification.service;

import com.connecthub.notification.entity.Notification;
import com.connecthub.notification.entity.UserEmailPreference;
import com.connecthub.notification.repository.NotificationRepository;
import com.connecthub.notification.repository.UserEmailPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MissedDmEmailScheduler — Periodic Email Digest for Unread Notifications
 *
 * PURPOSE:
 *   Runs every 5 minutes and sends a digest email to users who:
 *   1. Have opted in to email notifications (emailNotificationsEnabled=true in user_email_preferences).
 *   2. Have unread notifications that arrived more than 30 minutes ago and have not yet been
 *      included in any previous digest email (emailSent=false on the Notification row).
 *
 * WHY 30 MINUTES:
 *   Immediate notifications would cause email spam for active users. The 30-minute window
 *   ensures only genuinely missed messages trigger an email. A user who reads a notification
 *   within 30 min (isRead=true) is excluded — the query filters on isRead=false.
 *
 * DUPLICATE PREVENTION:
 *   After sending a digest, markEmailSent() bulk-updates all qualifying notifications to
 *   emailSent=true. This prevents the same notification from appearing in multiple digests
 *   even if the user remains offline across multiple scheduler runs.
 *
 * OPT-IN MODEL:
 *   Only users with a row in user_email_preferences are contacted. New users default to
 *   opted-in when they first toggle the preference ON via their profile settings.
 *   Users who have never set a preference are not emailed (no email address stored).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MissedDmEmailScheduler {

    private final NotificationRepository notifRepo;
    private final UserEmailPreferenceRepository emailPrefRepo;
    private final EmailSender emailSender;

    private static final int DIGEST_DELAY_MINUTES = 30;

    @Scheduled(fixedDelay = 5 * 60 * 1000) // every 5 minutes
    @Transactional
    public void sendMissedNotificationEmails() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(DIGEST_DELAY_MINUTES);
        List<UserEmailPreference> optedIn = emailPrefRepo.findByEmailNotificationsEnabledTrue();
        if (optedIn.isEmpty()) return;

        int totalSent = 0;
        for (UserEmailPreference pref : optedIn) {
            List<Notification> pending = notifRepo.findDigestCandidates(pref.getUserId(), threshold);
            if (pending.isEmpty()) continue;
            try {
                emailSender.sendMissedNotifications(pref.getEmail(), pending);
                notifRepo.markEmailSent(pref.getUserId(), threshold);
                totalSent++;
                log.debug("Missed-notification digest sent to user {} ({} notifications)", pref.getUserId(), pending.size());
            } catch (Exception e) {
                log.warn("Failed to send digest email to user {}: {}", pref.getUserId(), e.getMessage());
            }
        }
        if (totalSent > 0) {
            log.info("Missed-notification digest run complete: {} emails dispatched", totalSent);
        }
    }
}
