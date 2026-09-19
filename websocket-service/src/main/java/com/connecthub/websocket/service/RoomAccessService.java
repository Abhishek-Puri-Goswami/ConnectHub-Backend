package com.connecthub.websocket.service;

import com.connecthub.websocket.client.MessageServiceClient;
import com.connecthub.websocket.client.RoomServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authorization for real-time actions: may this user use this room's live channels, and did this
 * user author this message? Membership is owned by room-service; positive answers are cached briefly
 * so a chat burst does not turn into a REST call per frame. Fails closed when room-service is
 * unreachable. A removed member can therefore keep a live subscription for at most {@link #TTL_MS}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomAccessService {

    static final long TTL_MS = 15_000;
    private static final int MAX_CACHE_ENTRIES = 50_000;

    private final RoomServiceClient roomClient;
    private final MessageServiceClient messageClient;
    private final Map<String, Long> memberUntil = new ConcurrentHashMap<>();

    public boolean isMember(String roomId, int userId) {
        if (roomId == null || roomId.isBlank()) return false;
        String key = roomId + "|" + userId;
        long now = System.currentTimeMillis();
        Long until = memberUntil.get(key);
        if (until != null && until > now) return true;
        try {
            if (Boolean.TRUE.equals(roomClient.isMember(roomId, userId))) {
                if (memberUntil.size() > MAX_CACHE_ENTRIES) memberUntil.values().removeIf(t -> t <= now);
                memberUntil.put(key, now + TTL_MS);
                return true;
            }
        } catch (Exception e) {
            log.warn("Room membership check failed room={} user={}: {}", roomId, userId, e.getMessage());
        }
        memberUntil.remove(key);
        return false;
    }

    /** True only if the message exists in that room and was sent by the user. */
    public boolean isAuthor(String messageId, String roomId, int userId) {
        if (messageId == null || messageId.isBlank() || roomId == null) return false;
        try {
            Map<String, Object> info = messageClient.getSender(messageId);
            return info != null
                    && roomId.equals(String.valueOf(info.get("roomId")))
                    && String.valueOf(userId).equals(String.valueOf(info.get("senderId")));
        } catch (Exception e) {
            log.warn("Message author check failed message={} user={}: {}", messageId, userId, e.getMessage());
            return false;
        }
    }
}
