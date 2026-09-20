package com.connecthub.websocket.listener;

import com.connecthub.websocket.service.UnreadCountService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DeletionCleanupListenerTest {

    private final UnreadCountService unread = mock(UnreadCountService.class);
    private final DeletionCleanupListener listener = new DeletionCleanupListener(unread);

    @Test
    void roomDeleted_clearsThatRoomsCounters_ignoringBlankPayloads() {
        listener.onRoomDeleted("room-1");
        listener.onRoomDeleted(null);
        listener.onRoomDeleted(" ");
        verify(unread).clearRoom("room-1");
        verify(unread, times(1)).clearRoom(anyString());
    }

    @Test
    void userDeleted_clearsThatUsersCounters_ignoringBadPayloads() {
        listener.onUserDeleted("42");
        listener.onUserDeleted("abc");
        listener.onUserDeleted(null);
        verify(unread).clearUser(42);
        verify(unread, times(1)).clearUser(anyInt());
    }
}
