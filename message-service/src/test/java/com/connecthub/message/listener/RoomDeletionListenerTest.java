package com.connecthub.message.listener;

import com.connecthub.message.service.MessageService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RoomDeletionListenerTest {

    private final MessageService messageService = mock(MessageService.class);
    private final RoomDeletionListener listener = new RoomDeletionListener(messageService);

    @Test
    void roomDeleted_removesItsMessagesAndReactions() {
        listener.onRoomDeleted("room-1");
        verify(messageService).clearHistory("room-1");
    }

    @Test
    void blankOrNullPayload_isIgnored() {
        listener.onRoomDeleted(null);
        listener.onRoomDeleted(" ");
        listener.onRoomDeleted("null");
        verifyNoInteractions(messageService);
    }

    @Test
    void failure_isRethrown_soKafkaCanRetry() {
        doThrow(new RuntimeException("db")).when(messageService).clearHistory("room-2");
        assertThrows(RuntimeException.class, () -> listener.onRoomDeleted("room-2"));
    }
}
