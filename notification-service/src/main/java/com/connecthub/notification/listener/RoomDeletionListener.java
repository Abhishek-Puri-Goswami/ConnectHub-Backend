package com.connecthub.notification.listener;

import com.connecthub.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A room was deleted (room-service, topic room.deleted): drop the notifications that point at it. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomDeletionListener {

    private final NotificationRepository notificationRepository;

    @KafkaListener(topics = "room.deleted", groupId = "notification-service-room-deletion-group", properties = "auto.offset.reset=earliest")
    @Transactional
    public void onRoomDeleted(String roomId) {
        if (roomId == null || roomId.isBlank() || "null".equals(roomId)) {
            log.error("Ignoring room.deleted event with no room id");
            return;
        }
        int removed = notificationRepository.deleteAllByRoom(roomId.trim());
        log.info("Removed {} notifications of deleted room {}", removed, roomId);
    }
}
