package com.connecthub.websocket.handler;

import com.connecthub.websocket.client.MessageServiceClient;
import com.connecthub.websocket.config.RedisConfig;
import com.connecthub.websocket.dto.*;
import com.connecthub.websocket.service.DeliveryService;
import com.connecthub.websocket.service.MessagePersistenceService;
import com.connecthub.websocket.service.TypingService;
import com.connecthub.websocket.service.UnreadCountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ChatWebSocketHandler — STOMP Message Handler for All Real-Time Chat Events
 *
 * PURPOSE:
 *   This is the entry point for every real-time event sent from the frontend
 *   over the WebSocket/STOMP connection. The client publishes frames to
 *   /app/chat.{action} and Spring STOMP routes them to the @MessageMapping methods here.
 *
 * EVENTS HANDLED:
 *   /app/chat.send   — a user sends a new chat message
 *   /app/chat.typing — a user is currently typing in a room
 *   /app/chat.read   — a user has read messages up to a given point in a room
 *   /app/chat.react  — a user adds or removes an emoji reaction on a message
 *   /app/chat.edit   — a user edits the content of one of their messages
 *   /app/chat.delete — a user deletes one of their messages
 *
 * AUTHENTICATION:
 *   JwtChannelInterceptor validates the JWT in the STOMP CONNECT frame and sets
 *   the session principal to a StompPrincipal record containing { userId, username,
 *   subscriptionTier }. Every handler reads h.getUser() to get the sender's identity
 *   and drops the frame silently if the user is not authenticated.
 *
 * MESSAGE SEND FLOW (handleChat) — 4 steps:
 *   1. SYNCHRONOUS PERSISTENCE: Call message-service via Feign to save to MySQL.
 *      This runs synchronously BEFORE broadcast so the DB is the source of truth
 *      before any client displays the message. If a user refreshes immediately
 *      after sending, the message is already persisted and won't be "lost".
 *      On persist failure: fall through to Kafka-based eventual persistence so
 *      the real-time experience continues even if message-service is briefly down.
 *   2. BROADCAST via Redis pub/sub: The message is published to a Redis channel.
 *      RedisMessageSubscriber on every websocket-service pod receives it and pushes
 *      it to /topic/room/{roomId} via STOMP. This is how horizontal scaling works —
 *      each pod fans out to its own connected clients.
 *   3. KAFKA AUDIT: Published to "chat.messages.inbound" for offline notification
 *      delivery, analytics, and as a backup persistence path.
 *   4. ROOM TIMESTAMP: Async update to room.lastMessageAt so the sidebar sorts
 *      rooms by most recent activity after page refresh.
 *
 * CONTENT SANITIZATION:
 *   HtmlUtils.htmlEscape() is applied ONCE here, converting characters like < > & "
 *   into their HTML entity equivalents (&lt; &gt; &amp; &quot;) to prevent XSS.
 *   The message-service must NOT re-escape on save, or the content becomes double-encoded.
 *
 * REACTIONS, EDITS, DELETES — Redis fan-out:
 *   These events are published to dedicated Redis channels (REACTION_CHANNEL,
 *   EDIT_CHANNEL, DELETE_CHANNEL). RedisMessageSubscriber receives them and pushes
 *   to room-specific STOMP topics (/topic/room/{id}/reactions, /edit, /delete).
 *   This makes all events consistent — room-scoped topics, delivered to all pods.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler {

    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MessagePersistenceService persistenceService;
    private final MessageServiceClient messageServiceClient;
    private final TypingService typingService;
    private final DeliveryService deliveryService;
    private final UnreadCountService unreadCountService;

    /**
     * handleChat — processes an incoming chat message from a connected client.
     * Enriches the payload with server-side fields (senderId, timestamp, sanitized content),
     * persists synchronously, then broadcasts via Redis for cross-pod fan-out.
     */
    @MessageMapping("/chat.send")
    public void handleChat(@Payload ChatMessagePayload p, SimpMessageHeaderAccessor h) {
        try {
            if (h.getUser() == null) {
                log.warn("Chat send from unauthenticated session — dropping");
                return;
            }
            String uid = h.getUser().getName();
            var stomp = (com.connecthub.websocket.interceptor.JwtChannelInterceptor.StompPrincipal) h.getUser();
            String username = stomp.username();
            String subscriptionTier = stomp.subscriptionTier();

            /*
             * Assign a messageId if the client didn't provide one.
             * A UUID guarantees uniqueness even across multiple pod instances.
             */
            if (p.getMessageId() == null || p.getMessageId().isBlank()) {
                p.setMessageId(UUID.randomUUID().toString());
            }
            if (p.getRoomId() == null || p.getRoomId().isBlank()) {
                log.warn("Chat send with no roomId — dropping");
                return;
            }

            /*
             * Populate server-authoritative fields from the authenticated session.
             * The client cannot spoof these — they come from the JWT claim.
             */
            p.setSenderId(Integer.parseInt(uid));
            p.setSenderUsername(username);
            p.setSubscriptionTier(subscriptionTier != null && !subscriptionTier.isBlank() ? subscriptionTier : "FREE");
            p.setTimestamp(System.currentTimeMillis());
            if (p.getType() == null || p.getType().isBlank()) p.setType("TEXT");
            if (p.getDeliveryStatus() == null) p.setDeliveryStatus("SENT");

            /*
             * XSS sanitization — convert special HTML characters to entities once here.
             * The frontend's decodeHtml() reverses this for display so the content
             * renders correctly. message-service must store the already-escaped content as-is.
             */
            if (p.getContent() != null) p.setContent(HtmlUtils.htmlEscape(p.getContent()));

            /*
             * Step 1: Synchronous persistence via Feign call to message-service.
             * This guarantees the message is in MySQL BEFORE broadcasting so clients
             * can immediately reload history and see the message.
             * Failure here does NOT stop delivery — Kafka provides eventual persistence.
             */
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("messageId", p.getMessageId());
                body.put("roomId", p.getRoomId());
                body.put("senderId", p.getSenderId());
                body.put("content", p.getContent());
                body.put("type", p.getType());
                if (p.getMediaUrl() != null) body.put("mediaUrl", p.getMediaUrl());
                if (p.getThumbnailUrl() != null) body.put("thumbnailUrl", p.getThumbnailUrl());
                if (p.getReplyToMessageId() != null) body.put("replyToMessageId", p.getReplyToMessageId());

                Map<String, Object> saved = messageServiceClient.persistMessage(body, uid, p.getSubscriptionTier());
                if (saved != null) {
                    /*
                     * Use the DB-assigned sentAt and messageId so all clients display
                     * exactly what was stored, not the client's local timestamp.
                     */
                    Object dbSentAt = saved.get("sentAt");
                    if (dbSentAt != null) p.setSentAt(dbSentAt.toString());
                    Object dbId = saved.get("messageId");
                    if (dbId != null) p.setMessageId(dbId.toString());
                }
            } catch (Exception persistEx) {
                log.warn("Synchronous persist failed for msg {}, falling back to Kafka: {}",
                        p.getMessageId(), persistEx.getMessage());
            }

            /*
             * Step 2: Broadcast via Redis pub/sub.
             * Publishing once to CHAT_CHANNEL means RedisMessageSubscriber on every
             * websocket-service pod fans it out to /topic/room/{roomId}.
             * This is the mechanism that makes horizontal scaling work — all pods
             * deliver to their own clients from the same Redis message.
             */
            String json = mapper.writeValueAsString(p);
            redis.convertAndSend(RedisConfig.CHAT_CHANNEL, json);

            /*
             * Step 3: Kafka async audit and fallback persistence.
             * Fires asynchronously — used by notification-service for offline alerts,
             * analytics consumers, and as a secondary persistence path via KafkaMessageListener.
             */
            persistenceService.persistMessage(p);

            /*
             * Step 4: Update room.lastMessageAt async so the sidebar sorts by recent
             * activity after page refresh. Not on the critical delivery path.
             */
            deliveryService.updateRoomTimestamp(p.getRoomId(), uid);

            log.debug("Chat from user {} to room {} (id={})", uid, p.getRoomId(), p.getMessageId());
        } catch (Exception e) {
            log.error("Chat handler error", e);
        }
    }

    /**
     * handleTyping — broadcasts a typing indicator to all room members.
     * Records the typing state in Redis via TypingService with a 4-second TTL.
     * The TTL means the indicator disappears automatically if the client crashes.
     */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingIndicatorPayload p, SimpMessageHeaderAccessor h) {
        if (h.getUser() == null) return;
        int senderId = Integer.parseInt(h.getUser().getName());
        p.setSenderId(senderId);
        if (h.getUser() instanceof com.connecthub.websocket.interceptor.JwtChannelInterceptor.StompPrincipal sp) {
            p.setSenderUsername(sp.username());
        }
        typingService.setTyping(p.getRoomId(), senderId);
        messaging.convertAndSend("/topic/room/" + p.getRoomId() + "/typing", p);
    }

    /**
     * handleRead — processes a read receipt from the client.
     * Broadcasts it to the room topic so message senders can update their delivery status ticks.
     * Also persists lastReadAt via DeliveryService and resets the unread counter for this room.
     */
    @MessageMapping("/chat.read")
    public void handleRead(@Payload ReadReceiptPayload p, SimpMessageHeaderAccessor h) {
        if (h.getUser() == null) return;
        String uid = h.getUser().getName();
        p.setReaderId(Integer.parseInt(uid));
        messaging.convertAndSend("/topic/room/" + p.getRoomId() + "/read", p);
        deliveryService.persistLastRead(p.getRoomId(), uid);
        unreadCountService.reset(Integer.parseInt(uid), p.getRoomId());
    }

    /**
     * handleReaction — routes an emoji reaction event to all room members.
     * Published to REACTION_CHANNEL in Redis, where RedisMessageSubscriber
     * fans it out to /topic/room/{roomId}/reactions.
     */
    @MessageMapping("/chat.react")
    public void handleReaction(@Payload ReactionPayload p, SimpMessageHeaderAccessor h) {
        try {
            if (h.getUser() == null) return;
            p.setSenderId(Integer.parseInt(h.getUser().getName()));
            String json = mapper.writeValueAsString(p);
            redis.convertAndSend(RedisConfig.REACTION_CHANNEL, json);
        } catch (Exception e) {
            log.error("Reaction handler error", e);
        }
    }

    /**
     * handleEdit — routes a message edit event to all room members.
     * The new content is sanitized with HtmlUtils.htmlEscape before broadcasting.
     * Published to EDIT_CHANNEL; RedisMessageSubscriber delivers to /topic/room/{id}/edit.
     */
    @MessageMapping("/chat.edit")
    public void handleEdit(@Payload MessageEditPayload p, SimpMessageHeaderAccessor h) {
        try {
            if (h.getUser() == null) return;
            p.setEditorId(Integer.parseInt(h.getUser().getName()));
            if (p.getNewContent() != null) p.setNewContent(HtmlUtils.htmlEscape(p.getNewContent()));
            String json = mapper.writeValueAsString(p);
            redis.convertAndSend(RedisConfig.EDIT_CHANNEL, json);
        } catch (Exception e) {
            log.error("Edit handler error", e);
        }
    }

    /**
     * handleDelete — routes a message deletion event to all room members.
     * The deleterId is set from the authenticated session (cannot be spoofed).
     * Published to DELETE_CHANNEL; RedisMessageSubscriber delivers to /topic/room/{id}/delete.
     */
    @MessageMapping("/chat.delete")
    public void handleDelete(@Payload MessageDeletePayload p, SimpMessageHeaderAccessor h) {
        try {
            if (h.getUser() == null) return;
            p.setDeleterId(Integer.parseInt(h.getUser().getName()));
            String json = mapper.writeValueAsString(p);
            redis.convertAndSend(RedisConfig.DELETE_CHANNEL, json);
        } catch (Exception e) {
            log.error("Delete handler error", e);
        }
    }
}
