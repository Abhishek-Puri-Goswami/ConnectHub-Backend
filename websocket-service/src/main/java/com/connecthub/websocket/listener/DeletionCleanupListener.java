package com.connecthub.websocket.listener;

import com.connecthub.websocket.service.UnreadCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Removes the Redis unread counters that belong to deleted rooms and deleted accounts. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeletionCleanupListener {

    private final UnreadCountService unreadCountService;

    @KafkaListener(topics = "room.deleted", groupId = "websocket-service-room-deletion-group", properties = "auto.offset.reset=earliest")
    public void onRoomDeleted(String roomId) {
        if (roomId == null || roomId.isBlank() || "null".equals(roomId)) return;
        int removed = unreadCountService.clearRoom(roomId.trim());
        log.info("Removed {} unread counters of deleted room {}", removed, roomId);
    }

    @KafkaListener(topics = "auth.user.deleted", groupId = "websocket-service-user-deletion-group", properties = "auto.offset.reset=earliest")
    public void onUserDeleted(String userIdStr) {
        try {
            int removed = unreadCountService.clearUser(Integer.parseInt(userIdStr.trim()));
            log.info("Removed {} unread counters of deleted user {}", removed, userIdStr);
        } catch (NumberFormatException | NullPointerException e) {
            log.error("Invalid userId format in USER_DELETED event: {}", userIdStr);
        }
    }
}
