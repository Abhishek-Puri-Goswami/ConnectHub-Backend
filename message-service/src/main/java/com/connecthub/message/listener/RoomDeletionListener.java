package com.connecthub.message.listener;

import com.connecthub.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** A room was deleted (room-service, topic room.deleted): permanently remove its messages and their reactions. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomDeletionListener {

    private final MessageService messageService;

    @KafkaListener(topics = "room.deleted", groupId = "message-service-group", properties = "auto.offset.reset=earliest")
    public void onRoomDeleted(String roomId) {
        if (roomId == null || roomId.isBlank() || "null".equals(roomId)) {
            log.error("Ignoring room.deleted event with no room id");
            return;
        }
        try {
            messageService.clearHistory(roomId.trim());
            log.info("Removed messages and reactions of deleted room {}", roomId);
        } catch (Exception e) {
            log.error("Failed to remove data of deleted room {}: {}", roomId, e.getMessage());
            throw e; // let Kafka retry / dead-letter it
        }
    }
}
