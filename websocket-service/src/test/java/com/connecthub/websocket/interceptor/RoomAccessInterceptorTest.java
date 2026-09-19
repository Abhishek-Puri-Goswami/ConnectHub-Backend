package com.connecthub.websocket.interceptor;

import com.connecthub.websocket.service.RoomAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomAccessInterceptorTest {

    private RoomAccessService roomAccess;
    private RoomAccessInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        roomAccess = mock(RoomAccessService.class);
        interceptor = new RoomAccessInterceptor(roomAccess);
        when(roomAccess.isMember("room-1", 7)).thenReturn(true);
    }

    private Message<byte[]> frame(StompCommand cmd, String destination, String userId) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(cmd);
        if (destination != null) acc.setDestination(destination);
        if (userId != null) acc.setUser(new JwtChannelInterceptor.StompPrincipal(userId, "u"));
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    @Test
    void subscribeToRoomTopic_memberAllowed_allSubTopicsCovered() {
        for (String suffix : new String[]{"", "/typing", "/read", "/edit", "/delete", "/reactions", "/pin"}) {
            assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room/room-1" + suffix, "7"), channel));
        }
    }

    @Test
    void subscribeToRoomTopic_nonMemberDenied() {
        assertThrows(IllegalStateException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room/room-1", "9"), channel));
        assertThrows(IllegalStateException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room/room-1/typing", "9"), channel));
    }

    @Test
    void subscribeToOtherUsersQueue_denied_ownQueueAllowed() {
        assertThrows(IllegalStateException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/8/queue/messages", "7"), channel));
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/7/queue/messages", "7"), channel));
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/errors", "7"), channel));
    }

    @Test
    void globalTopicsAllowed_unknownTopicsDenied() {
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/presence", "7"), channel));
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/broadcast", "7"), channel));
        assertThrows(IllegalStateException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/admin/secrets", "7"), channel));
    }

    @Test
    void framesWithoutAuthenticatedPrincipal_rejected_connectPasses() {
        assertThrows(IllegalStateException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", null), channel));
        assertThrows(IllegalStateException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/presence", null), channel));
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null), channel));
    }
}
