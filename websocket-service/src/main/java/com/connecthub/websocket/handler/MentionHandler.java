package com.connecthub.websocket.handler;

import com.connecthub.websocket.client.AuthServiceClient;
import com.connecthub.websocket.client.NotificationServiceClient;
import com.connecthub.websocket.dto.ChatMessagePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MentionHandler — Parses @username mentions from chat messages and sends MENTION notifications.
 *
 * Called after a message is successfully persisted by ChatWebSocketHandler. For each
 * @username found in the message content, looks up the user via auth-service and sends
 * a notification to the mentioned user (unless the mention is self-referential).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MentionHandler {

    private final AuthServiceClient authServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_]+)");
    private static final int CONTENT_PREVIEW_LENGTH = 100;

    /**
     * handle — parses mentions from the message and dispatches notifications.
     * Silently swallows all errors so a mention failure never disrupts message delivery.
     */
    public void handle(ChatMessagePayload msg) {
        if (msg.getContent() == null || msg.getContent().isBlank()) return;
        try {
            Matcher m = MENTION_PATTERN.matcher(msg.getContent());
            while (m.find()) {
                String mentionedUsername = m.group(1);
                notifyMentioned(msg, mentionedUsername);
            }
        } catch (Exception e) {
            log.warn("Mention handling failed for message {}: {}", msg.getMessageId(), e.getMessage());
        }
    }

    private void notifyMentioned(ChatMessagePayload msg, String mentionedUsername) {
        try {
            List<Map<String, Object>> results = authServiceClient.searchUsers(mentionedUsername);
            if (results == null || results.isEmpty()) return;

            // Find exact username match (search may return prefix matches)
            Integer recipientId = results.stream()
                    .filter(u -> mentionedUsername.equalsIgnoreCase(String.valueOf(u.get("username"))))
                    .map(u -> {
                        Object id = u.get("userId");
                        return id instanceof Integer ? (Integer) id : Integer.parseInt(id.toString());
                    })
                    .findFirst()
                    .orElse(null);

            if (recipientId == null) return;
            if (recipientId.equals(msg.getSenderId())) return; // skip self-mentions

            String preview = msg.getContent().length() > CONTENT_PREVIEW_LENGTH
                    ? msg.getContent().substring(0, CONTENT_PREVIEW_LENGTH) + "…"
                    : msg.getContent();

            Map<String, Object> notification = new HashMap<>();
            notification.put("recipientId", recipientId);
            notification.put("actorId", msg.getSenderId());
            notification.put("type", "MENTION");
            notification.put("title", "@" + msg.getSenderUsername() + " mentioned you");
            notification.put("message", preview);
            notification.put("roomId", msg.getRoomId());
            notification.put("messageId", msg.getMessageId());

            notificationServiceClient.createNotification(notification);
            log.debug("Mention notification sent: user {} mentioned @{} in room {}",
                    msg.getSenderId(), mentionedUsername, msg.getRoomId());
        } catch (Exception e) {
            log.warn("Failed to notify mention @{}: {}", mentionedUsername, e.getMessage());
        }
    }
}
