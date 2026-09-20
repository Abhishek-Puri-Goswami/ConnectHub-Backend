package com.connecthub.notification.listener;

import com.connecthub.notification.repository.DeviceTokenRepository;
import com.connecthub.notification.repository.NotificationRepository;
import com.connecthub.notification.repository.UserEmailPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * An account was deleted (auth-service, topic auth.user.deleted): remove everything notification-service keeps
 * about the user — their notifications, push (FCM) device tokens and email preference. Without this the device
 * tokens kept receiving pushes meant for a person who no longer exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletionListener {

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserEmailPreferenceRepository emailPreferenceRepository;

    @KafkaListener(topics = "auth.user.deleted", groupId = "notification-service-user-deletion-group", properties = "auto.offset.reset=earliest")
    @Transactional
    public void onUserDeleted(String userIdStr) {
        int userId;
        try {
            userId = Integer.parseInt(userIdStr.trim());
        } catch (NumberFormatException | NullPointerException e) {
            log.error("Invalid userId format in USER_DELETED event: {}", userIdStr);
            return;
        }
        int notifications = notificationRepository.deleteAllByRecipient(userId);
        deviceTokenRepository.deleteByUserId(userId);
        emailPreferenceRepository.deleteById(userId);
        log.info("Removed {} notifications, device tokens and email preference of deleted user {}", notifications, userId);
    }
}
