package com.connecthub.websocket.service;

import com.connecthub.websocket.client.MessageServiceClient;
import com.connecthub.websocket.client.RoomServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomAccessServiceTest {

    private RoomServiceClient roomClient;
    private MessageServiceClient messageClient;
    private RoomAccessService svc;

    @BeforeEach
    void setUp() {
        roomClient = mock(RoomServiceClient.class);
        messageClient = mock(MessageServiceClient.class);
        svc = new RoomAccessService(roomClient, messageClient);
    }

    @Test
    void member_isCached_soBurstsDoNotHitRoomService() {
        when(roomClient.isMember("r1", 1)).thenReturn(true);
        assertTrue(svc.isMember("r1", 1));
        assertTrue(svc.isMember("r1", 1));
        verify(roomClient, times(1)).isMember("r1", 1);
    }

    @Test
    void nonMember_isNeverCached() {
        when(roomClient.isMember("r1", 2)).thenReturn(false);
        assertFalse(svc.isMember("r1", 2));
        when(roomClient.isMember("r1", 2)).thenReturn(true); // joined a moment later
        assertTrue(svc.isMember("r1", 2));
    }

    @Test
    void failsClosedWhenRoomServiceIsDown_andForBlankRoom() {
        when(roomClient.isMember("r1", 3)).thenThrow(new RuntimeException("down"));
        assertFalse(svc.isMember("r1", 3));
        assertFalse(svc.isMember(null, 3));
        assertFalse(svc.isMember(" ", 3));
    }

    @Test
    void isAuthor_requiresMatchingSenderAndRoom() {
        when(messageClient.getSender("m1")).thenReturn(Map.of("senderId", 5, "roomId", "r1"));
        assertTrue(svc.isAuthor("m1", "r1", 5));
        assertFalse(svc.isAuthor("m1", "r1", 6));      // someone else's message
        assertFalse(svc.isAuthor("m1", "other", 5));   // message belongs to a different room
    }

    @Test
    void isAuthor_failsClosed() {
        when(messageClient.getSender("gone")).thenThrow(new RuntimeException("404"));
        assertFalse(svc.isAuthor("gone", "r1", 5));
        assertFalse(svc.isAuthor(null, "r1", 5));
    }
}
