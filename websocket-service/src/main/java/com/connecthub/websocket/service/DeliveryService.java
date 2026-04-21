package com.connecthub.websocket.service;

import com.connecthub.websocket.client.RoomServiceClient;
import com.connecthub.websocket.dto.ChatMessagePayload;
import com.connecthub.websocket.dto.RoomMemberDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * DeliveryService — Guaranteed Message Delivery to All Room Members
 *
 * PURPOSE:
 *   Ensures that every room member receives a message regardless of whether
 *   they are currently connected. It handles the "last mile" delivery problem:
 *   after a message is broadcast to the room topic, this service additionally
 *   delivers it to each member personally, queuing it for offline members.
 *
 * DELIVERY STRATEGY (three layers):
 *   1. ONLINE delivery via STOMP personal queue:
 *      convertAndSendToUser(id, "/queue/messages", msg) delivers to /user/{id}/queue/messages.
 *      This is a no-op if the user is not connected — Spring STOMP simply discards it.
 *      We ALWAYS attempt this for every non-sender member, even if we think they're online,
 *      to avoid race conditions where presence tracking lags briefly during reconnects.
 *
 *   2. OFFLINE queuing in Redis:
 *      For users detected as offline (not in the "presence:online" Redis set),
 *      the message JSON is pushed to a Redis list "pending:messages:{userId}" with a
 *      7-day TTL. When the user reconnects, flushPendingMessages() drains this list
 *      and delivers each queued message in order.
 *
 *   3. OFFLINE notification via Kafka:
 *      For truly offline users, a notification event is published to the
 *      "notifications.offline" Kafka topic. notification-service consumes this to
 *      create a persistent in-app notification (shown in the bell icon) so the user
 *      knows they have unread messages when they next log in.
 *
 * ASYNC EXECUTION:
 *   deliverToRoomMembers(), updateRoomTimestamp(), and persistLastRead() are all
 *   annotated @Async("asyncExecutor"). They run on a separate thread pool so the
 *   Redis message broadcast (the primary delivery) is never blocked by member lookup
 *   or offline queuing. The "asyncExecutor" bean is configured in AppConfig.
 *
 * flushPendingMessages() — called on reconnect:
 *   When a user connects their WebSocket, WebSocketEventListener calls
 *   flushPendingMessagesWithDelay() which waits 800ms (to let the STOMP session
 *   fully register) then drains the pending Redis list one message at a time,
 *   delivering each to the user's personal STOMP queue.
 *
 * UNREAD COUNTER:
 *   increment() is called for every non-sender member regardless of online status.
 *   The frontend resets the counter via /chat.read when the user opens the room.
 */
@SuppressWarnings("null")
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final RoomServiceClient roomServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UnreadCountService unreadCountService;

    private static final String PENDING_PREFIX  = "pending:messages:";
    private static final String ONLINE_SET      = "presence:online";
    private static final long   PENDING_TTL_DAYS = 7;

    /**
     * deliverToRoomMembers — delivers a message to every member of a room.
     * Fetches the member list from room-service via Feign, then for each non-sender:
     * - Increments their unread count
     * - Pushes via STOMP (always attempted — harmless no-op for offline users)
     * - Queues in Redis and creates a notification if they are offline
     */
    @Async("asyncExecutor")
    public void deliverToRoomMembers(ChatMessagePayload msg) {
        try {
            List<RoomMemberDto> members = roomServiceClient.getRoomMembers(
                    msg.getRoomId(), String.valueOf(msg.getSenderId()));

            for (RoomMemberDto member : members) {
                Integer memberId = member.getUserId();
                if (memberId.equals(msg.getSenderId())) continue;

                unreadCountService.increment(memberId, msg.getRoomId());

                /*
                 * Always push via STOMP even if presence says offline.
                 * This handles the reconnect race condition where the user just
                 * connected but hasn't updated their presence key yet.
                 */
                messaging.convertAndSendToUser(memberId.toString(), "/queue/messages", msg);

                if (!isUserOnline(memberId)) {
                    queuePendingMessage(memberId, msg);
                    createNotification(memberId, msg);
                }
            }
        } catch (Exception e) {
            log.error("Failed to deliver message to room {} members: {}", msg.getRoomId(), e.getMessage());
        }
    }

    /**
     * queuePendingMessage — stores a message in a Redis list for an offline user.
     * The list acts as a queue: rightPush adds to the tail, flushPendingMessages
     * uses leftPop to drain from the head (FIFO order), preserving message order.
     * The 7-day TTL means messages older than a week are automatically discarded.
     */
    private void queuePendingMessage(Integer userId, ChatMessagePayload msg) {
        try {
            String key = PENDING_PREFIX + userId;
            String json = mapper.writeValueAsString(msg);
            redis.opsForList().rightPush(key, json);
            redis.expire(key, PENDING_TTL_DAYS, TimeUnit.DAYS);
            log.debug("Queued pending message for offline user {}", userId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize pending message for user {}", userId, e);
        }
    }

    /**
     * createNotification — publishes a Kafka event for offline notification creation.
     * notification-service consumes this and creates a persistent in-app notification
     * and optionally sends an email/push notification. The preview is truncated to
     * 100 characters to keep the notification record small.
     */
    private void createNotification(Integer recipientId, ChatMessagePayload msg) {
        try {
            Map<String, Object> notif = new HashMap<>();
            notif.put("recipientId", recipientId);
            notif.put("actorId", msg.getSenderId());
            notif.put("type", "NEW_MESSAGE");
            notif.put("title", "New message from " + (msg.getSenderUsername() != null ? msg.getSenderUsername() : "someone"));
            String preview = msg.getContent() != null
                    ? msg.getContent().substring(0, Math.min(msg.getContent().length(), 100))
                    : "(media)";
            notif.put("message", preview);
            notif.put("roomId", msg.getRoomId());
            notif.put("messageId", msg.getMessageId());
            kafkaTemplate.send("notifications.offline", notif);
        } catch (Exception e) {
            log.warn("Failed to create notification for user {}: {}", recipientId, e.getMessage());
        }
    }

    /**
     * flushPendingMessages — drains all queued messages for a reconnecting user.
     * Pops messages one at a time from the Redis list and delivers via STOMP.
     * Stops when the list is empty (leftPop returns null).
     */
    public void flushPendingMessages(String userId) {
        String key = PENDING_PREFIX + userId;
        Long size = redis.opsForList().size(key);
        if (size == null || size == 0) return;

        log.info("Flushing {} pending messages to user {}", size, userId);
        int flushed = 0;
        while (true) {
            String json = redis.opsForList().leftPop(key);
            if (json == null) break;
            try {
                ChatMessagePayload msg = mapper.readValue(json, ChatMessagePayload.class);
                messaging.convertAndSendToUser(userId, "/queue/messages", msg);
                flushed++;
            } catch (Exception e) {
                log.error("Failed to deserialize pending message for user {}", userId, e);
            }
        }
        log.info("Flushed {} pending messages to user {}", flushed, userId);
    }

    /**
     * updateRoomTimestamp — async call to room-service to update lastMessageAt.
     * Runs on the async executor so it doesn't block the broadcast path.
     */
    @Async("asyncExecutor")
    public void updateRoomTimestamp(String roomId, String senderId) {
        try {
            roomServiceClient.updateLastMessageAt(roomId, senderId);
        } catch (Exception e) {
            log.warn("Failed to update room timestamp for {}: {}", roomId, e.getMessage());
        }
    }

    /**
     * persistLastRead — async call to room-service to update a member's lastReadAt.
     * Called when the user sends a /chat.read receipt so the backend's unread
     * count queries stay accurate across sessions.
     */
    @Async("asyncExecutor")
    public void persistLastRead(String roomId, String userId) {
        try {
            roomServiceClient.updateLastRead(roomId, userId, userId);
        } catch (Exception e) {
            log.warn("Failed to persist lastReadAt for user {} in room {}: {}", userId, roomId, e.getMessage());
        }
    }

    /**
     * flushPendingMessagesWithDelay — called from the WebSocket connect event handler.
     * Sleeps 800ms to allow the STOMP session to fully register before flushing,
     * preventing a race condition where the session isn't ready to receive messages.
     */
    @Async("asyncExecutor")
    public void flushPendingMessagesWithDelay(String userId) {
        try {
            Thread.sleep(800);
            flushPendingMessages(userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * pushNotification — delivers a real-time notification to an online user.
     * Uses the STOMP user destination so only the specific user receives it.
     * This is a no-op if the user has no active STOMP session on this pod.
     */
    public void pushNotification(Integer userId, Map<String, Object> notification) {
        messaging.convertAndSendToUser(userId.toString(), "/queue/notifications", notification);
    }

    /**
     * isUserOnline — checks whether a user is in the Redis "presence:online" set.
     * presence-service adds users to this set on heartbeat and removes them on disconnect.
     */
    private boolean isUserOnline(Integer userId) {
        return Boolean.TRUE.equals(
                redis.opsForSet().isMember(ONLINE_SET, String.valueOf(userId)));
    }
}
