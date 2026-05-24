package com.connecthub.websocket.handler;

import com.connecthub.websocket.client.AuthServiceClient;
import com.connecthub.websocket.client.NotificationServiceClient;
import com.connecthub.websocket.dto.ChatMessagePayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentionHandlerTest {

    @Mock private AuthServiceClient authServiceClient;
    @Mock private NotificationServiceClient notificationServiceClient;

    @InjectMocks private MentionHandler mentionHandler;

    private ChatMessagePayload msg(int senderId, String senderUsername, String content) {
        ChatMessagePayload p = new ChatMessagePayload();
        p.setSenderId(senderId);
        p.setSenderUsername(senderUsername);
        p.setContent(content);
        p.setRoomId("room1");
        p.setMessageId("msg1");
        return p;
    }

    // ── No mention ────────────────────────────────────────────────────────────

    @Test
    void handle_nullContent_doesNothing() {
        ChatMessagePayload p = msg(1, "alice", null);
        mentionHandler.handle(p);
        verifyNoInteractions(authServiceClient, notificationServiceClient);
    }

    @Test
    void handle_blankContent_doesNothing() {
        ChatMessagePayload p = msg(1, "alice", "   ");
        mentionHandler.handle(p);
        verifyNoInteractions(authServiceClient, notificationServiceClient);
    }

    @Test
    void handle_noMentions_doesNothing() {
        ChatMessagePayload p = msg(1, "alice", "Hello everyone, how are you?");
        mentionHandler.handle(p);
        verifyNoInteractions(authServiceClient, notificationServiceClient);
    }

    // ── Single mention ────────────────────────────────────────────────────────

    @Test
    void handle_validMention_sendsNotification() {
        ChatMessagePayload p = msg(1, "alice", "Hey @bob check this out!");
        when(authServiceClient.searchUsers("bob")).thenReturn(
                List.of(Map.of("userId", 2, "username", "bob")));

        mentionHandler.handle(p);

        verify(notificationServiceClient).createNotification(argThat(n ->
                "MENTION".equals(n.get("type")) &&
                Integer.valueOf(2).equals(n.get("recipientId")) &&
                Integer.valueOf(1).equals(n.get("actorId"))
        ));
    }

    @Test
    void handle_selfMention_skipsNotification() {
        ChatMessagePayload p = msg(1, "alice", "I @alice did it!");
        when(authServiceClient.searchUsers("alice")).thenReturn(
                List.of(Map.of("userId", 1, "username", "alice")));

        mentionHandler.handle(p);

        verifyNoInteractions(notificationServiceClient);
    }

    @Test
    void handle_usernameNotFound_skipsNotification() {
        ChatMessagePayload p = msg(1, "alice", "Hey @ghost!");
        when(authServiceClient.searchUsers("ghost")).thenReturn(List.of());

        mentionHandler.handle(p);

        verifyNoInteractions(notificationServiceClient);
    }

    @Test
    void handle_noExactUsernameMatch_skipsNotification() {
        ChatMessagePayload p = msg(1, "alice", "Hey @bob!");
        // Search returns "bobby" (prefix match) but not exact "bob"
        when(authServiceClient.searchUsers("bob")).thenReturn(
                List.of(Map.of("userId", 5, "username", "bobby")));

        mentionHandler.handle(p);

        verifyNoInteractions(notificationServiceClient);
    }

    // ── Multiple mentions ─────────────────────────────────────────────────────

    @Test
    void handle_multipleMentions_sendsNotificationForEach() {
        ChatMessagePayload p = msg(1, "alice", "@bob and @carol join the call!");
        when(authServiceClient.searchUsers("bob")).thenReturn(
                List.of(Map.of("userId", 2, "username", "bob")));
        when(authServiceClient.searchUsers("carol")).thenReturn(
                List.of(Map.of("userId", 3, "username", "carol")));

        mentionHandler.handle(p);

        verify(notificationServiceClient, times(2)).createNotification(any());
    }

    // ── Long content preview truncation ──────────────────────────────────────

    @Test
    void handle_longContent_truncatesPreviewTo100Chars() {
        String longContent = "@bob " + "x".repeat(200);
        ChatMessagePayload p = msg(1, "alice", longContent);
        when(authServiceClient.searchUsers("bob")).thenReturn(
                List.of(Map.of("userId", 2, "username", "bob")));

        mentionHandler.handle(p);

        verify(notificationServiceClient).createNotification(argThat(n -> {
            String message = (String) n.get("message");
            return message != null && message.endsWith("…") && message.length() == 101;
        }));
    }

    // ── Error resilience ──────────────────────────────────────────────────────

    @Test
    void handle_authClientThrows_doesNotPropagate() {
        ChatMessagePayload p = msg(1, "alice", "@bob hi");
        when(authServiceClient.searchUsers("bob")).thenThrow(new RuntimeException("auth down"));

        assertDoesNotThrow(() -> mentionHandler.handle(p));
        verifyNoInteractions(notificationServiceClient);
    }

    @Test
    void handle_notifClientThrows_doesNotPropagate() {
        ChatMessagePayload p = msg(1, "alice", "@bob hi");
        when(authServiceClient.searchUsers("bob")).thenReturn(
                List.of(Map.of("userId", 2, "username", "bob")));
        doThrow(new RuntimeException("notif down")).when(notificationServiceClient).createNotification(any());

        assertDoesNotThrow(() -> mentionHandler.handle(p));
    }
}
