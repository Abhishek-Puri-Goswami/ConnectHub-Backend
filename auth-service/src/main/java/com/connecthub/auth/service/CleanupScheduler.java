package com.connecthub.auth.service;

import com.connecthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CleanupScheduler — Periodic Cleanup of Stale Accounts
 *
 * PURPOSE:
 *   Removes ephemeral accounts that are no longer useful:
 *   1. Unverified accounts — users who registered but never confirmed their email.
 *      After 24 hours the OTP has expired anyway, so these rows are dead weight.
 *   2. Guest accounts — temporary anonymous users created via /auth/guest.
 *      These are meant for quick demos; after 24 hours they are cleaned up to
 *      prevent the user table from growing unboundedly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduler {

    private final UserRepository userRepository;

    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional
    public void cleanupStaleAccounts() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);

        // Clean up unverified accounts (registered but never confirmed email)
        userRepository.deleteByEmailVerifiedFalseAndCreatedAtBefore(threshold);
        log.info("Cleaned up unverified accounts older than 24 hours");

        // Clean up expired guest accounts (temporary anonymous sessions)
        int guestCount = userRepository.deleteGuestAccountsOlderThan(threshold);
        if (guestCount > 0) {
            log.info("Cleaned up {} expired guest accounts older than 24 hours", guestCount);
        }
    }
}

