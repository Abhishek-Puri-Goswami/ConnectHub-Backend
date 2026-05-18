package com.connecthub.websocket.service;

import com.connecthub.websocket.config.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomNotificationService {

    private final SimpMessagingTemplate messaging;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;

    /**
     * processRoomCreated — notifies all room members when a new room is created.
     *
     * Each member receives a ROOM_CREATED notification on their personal queue so
     * their sidebar refreshes immediately without a page reload.
     *
     * IMPORTANT: We publish each notification through Redis pub/sub (NOTIF_CHANNEL)
     * instead of calling convertAndSendToUser() directly. Direct STOMP calls only
     * reach clients connected to THIS pod. Routing through Redis ensures the message
     * reaches the correct pod in a multi-instance deployment, and also guarantees
     * delivery even when the recipient's STOMP session was registered after this
     * Kafka event was consumed (avoids the silent no-op race condition).
     */
    @KafkaListener(
            topics = "room.created",
            groupId = "websocket-service-room-group"
    )
    public void processRoomCreated(
            @Payload String payloadJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.debug("Consuming {}@{}", topic, offset);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.convertValue(
                    objectMapper.readValue(payloadJson, Map.class), Map.class);

            payload.put("type", "ROOM_CREATED");

            Object memberIdsObj = payload.get("memberIds");
            if (memberIdsObj instanceof List<?> memberIds) {
                for (Object memberIdObj : memberIds) {
                    if (memberIdObj != null) {
                        // Route through Redis pub/sub so RedisMessageSubscriber fans it out
                        // to the correct websocket-service pod via convertAndSendToUser.
                        // This also adds a recipientId field so the subscriber knows who to target.
                        Map<String, Object> notifPayload = new java.util.HashMap<>(payload);
                        notifPayload.put("recipientId", Integer.parseInt(memberIdObj.toString()));
                        redis.convertAndSend(RedisConfig.NOTIF_CHANNEL, objectMapper.writeValueAsString(notifPayload));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process room.created event", e);
        }
    }
}
