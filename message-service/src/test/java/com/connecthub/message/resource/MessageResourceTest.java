package com.connecthub.message.resource;

import com.connecthub.message.client.RoomClient;
import com.connecthub.message.entity.Message;
import com.connecthub.message.entity.MessageReaction;
import com.connecthub.message.exception.ForbiddenException;
import com.connecthub.message.service.MessageService;
import com.connecthub.message.service.RoomAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Room "r1": user 2 is a MEMBER, user 3 an ADMIN; user 9 is an outsider. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageResourceTest {

    @Mock private MessageService svc;
    @Mock private RoomClient roomClient;

    private MessageResource res;

    @BeforeEach
    void setUp() {
        res = new MessageResource(svc, new RoomAccess(roomClient));
        when(roomClient.isMember(any(), anyInt())).thenReturn(false);
        when(roomClient.isMember("r1", 2)).thenReturn(true);
        when(roomClient.isMember("r1", 3)).thenReturn(true);
        when(roomClient.members("r1", "3")).thenReturn(List.of(
                Map.of("userId", 2, "role", "MEMBER"), Map.of("userId", 3, "role", "ADMIN")));
        when(roomClient.members("r1", "2")).thenReturn(List.of(
                Map.of("userId", 2, "role", "MEMBER"), Map.of("userId", 3, "role", "ADMIN")));
        when(svc.roomIdOf("m1")).thenReturn("r1");
    }

    private static Message msgIn(String roomId) {
        Message m = new Message();
        m.setRoomId(roomId);
        return m;
    }

    @Test
    void send_memberOk_andSenderIdIsForcedToCaller() {
        Message msg = msgIn("r1");
        msg.setSenderId(3); // spoofed
        when(svc.send(msg, "PRO")).thenReturn(msg);
        assertEquals(HttpStatus.CREATED, res.send(msg, 2, "PRO").getStatusCode());
        assertEquals(2, msg.getSenderId());
    }

    @Test
    void send_nonMember_forbidden() {
        assertThrows(ForbiddenException.class, () -> res.send(msgIn("r1"), 9, "FREE"));
        verify(svc, never()).send(any(), any());
    }

    @Test
    void send_failsClosedWhenRoomServiceDown() {
        when(roomClient.isMember("r1", 2)).thenThrow(new RuntimeException("room-service down"));
        assertThrows(ForbiddenException.class, () -> res.send(msgIn("r1"), 2, "FREE"));
    }

    @Test
    void get_memberOk_pageSizeCapped() {
        LocalDateTime now = LocalDateTime.now();
        when(svc.getMessages("r1", now, 100)).thenReturn(List.of(new Message()));
        assertEquals(HttpStatus.OK, res.get("r1", now, 100000, 2).getStatusCode());
        verify(svc).getMessages("r1", now, 100);
    }

    @Test
    void get_nonMember_forbidden() {
        assertThrows(ForbiddenException.class, () -> res.get("r1", null, 50, 9));
        verify(svc, never()).getMessages(any(), any(), anyInt());
    }

    @Test
    void edit_and_delete_delegateAuthorCheckToService() {
        when(svc.edit("m1", "text", 1)).thenReturn(new Message());
        assertEquals(HttpStatus.OK, res.edit("m1", 1, Map.of("content", "text")).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, res.delete("m1", 1).getStatusCode());
        verify(svc).delete("m1", 1);
    }

    @Test
    void search_memberOk_nonMemberForbidden() {
        when(svc.search("r1", "k")).thenReturn(List.of(new Message()));
        assertEquals(HttpStatus.OK, res.search("r1", "k", 2).getStatusCode());
        assertThrows(ForbiddenException.class, () -> res.search("r1", "k", 9));
    }

    @Test
    void status_internalOnly() {
        assertEquals(HttpStatus.NO_CONTENT, res.status("m1", Map.of("status", "READ"), "websocket-service").getStatusCode());
        verify(svc).updateStatus("m1", "READ");
        assertThrows(ForbiddenException.class, () -> res.status("m1", Map.of("status", "READ"), null));
    }

    @Test
    void unread_memberOnly() {
        LocalDateTime now = LocalDateTime.now();
        when(svc.unreadCount("r1", now)).thenReturn(5L);
        assertEquals(HttpStatus.OK, res.unread("r1", now, 2).getStatusCode());
        assertThrows(ForbiddenException.class, () -> res.unread("r1", now, 9));
    }

    @Test
    void clear_roomAdminOk_memberAndOutsiderForbidden_platformAdminOk() {
        assertEquals(HttpStatus.NO_CONTENT, res.clear("r1", 3, "USER").getStatusCode());
        verify(svc).clearHistory("r1");
        assertThrows(ForbiddenException.class, () -> res.clear("r1", 2, "USER"));
        assertThrows(ForbiddenException.class, () -> res.clear("r1", 9, "USER"));
        verify(svc, times(1)).clearHistory("r1");
        assertEquals(HttpStatus.NO_CONTENT, res.clear("r1", 9, "PLATFORM_ADMIN").getStatusCode());
    }

    @Test
    void reactions_requireMembershipOfTheMessagesRoom() {
        when(svc.addReaction("m1", 2, "👍")).thenReturn(new MessageReaction());
        assertEquals(HttpStatus.CREATED, res.react("m1", 2, Map.of("emoji", "👍")).getStatusCode());
        assertThrows(ForbiddenException.class, () -> res.react("m1", 9, Map.of("emoji", "👍")));
        assertThrows(ForbiddenException.class, () -> res.unreact("m1", 9, "👍"));
        assertThrows(ForbiddenException.class, () -> res.reactions("m1", 9));
        verify(svc, never()).removeReaction(any(), anyInt(), any());
    }

    @Test
    void unreact_and_list_memberOk() {
        assertEquals(HttpStatus.NO_CONTENT, res.unreact("m1", 2, "👍").getStatusCode());
        verify(svc).removeReaction("m1", 2, "👍");
        when(svc.getReactions("m1")).thenReturn(List.of(new MessageReaction()));
        assertEquals(HttpStatus.OK, res.reactions("m1", 2).getStatusCode());
    }

    @Test
    void countToday_internalOrPlatformAdminOnly() {
        when(svc.countToday()).thenReturn(7L);
        assertEquals(HttpStatus.OK, res.countToday("auth-service", "").getStatusCode());
        assertEquals(HttpStatus.OK, res.countToday(null, "PLATFORM_ADMIN").getStatusCode());
        assertThrows(ForbiddenException.class, () -> res.countToday(null, "USER"));
    }
}
